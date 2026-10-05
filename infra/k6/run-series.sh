#!/usr/bin/env bash
# Runs write-path.js at several fixed rates and prints one row per rate.
#
# A series of steady plateaus rather than one ramp: in a ramp the rate and the latency move together, so the
# resulting graph cannot answer "what was p99 at this load" about any load in particular. Each run here holds
# one rate, and the row says what the service did at it.
#
# After every run it waits for the rows to arrive before starting the next one. That wait is the point of the
# whole exercise and the easiest thing to get wrong by hand: ingest answers 202 as soon as the event is on its
# way to Kafka, so a run that "finished" is a run whose rows may still be in flight. Comparing what was
# accepted with what was stored is only meaningful once the pipeline has caught up — and how long that takes
# is itself one of the numbers worth reporting.
#
# Usage, from the repository root, against the stack in docs/performance.md:
#   infra/k6/run-series.sh 250 500 1000 2000
set -euo pipefail

cd "$(dirname "$0")/../.."

if [ "$#" -gt 0 ]; then RATES=("$@"); else RATES=(250 500 1000 2000); fi

DURATION="${DURATION:-2m}"
DOMAIN="${LOADTEST_DOMAIN:-loadtest.example}"
PROMETHEUS="${PROMETHEUS:-http://localhost:19090}"
PROJECT="wholestory-loadtest"
COMPOSE=(docker compose -p "$PROJECT" --env-file infra/k6/loadtest.env
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.loadtest.yml)
PSQL=(docker exec "${PROJECT}-postgres-1" psql -U postgres -d wholestory -At -c)
mkdir -p infra/k6/results

site_id=$("${PSQL[@]}" "select id from sites.sites where domain = '${DOMAIN}' and deleted_at is null")
if [ -z "$site_id" ]; then
  echo "No live site for ${DOMAIN}. Register it first; ingest only accepts domains it heard about on" >&2
  echo "site-events (ADR 0019), and without it every event is rejected with 400 unknown_domain." >&2
  exit 1
fi
echo "site ${DOMAIN} = ${site_id}"

stored() { "${PSQL[@]}" "select count(*) from analytics.events where site_id = '${site_id}'"; }
sessions() { "${PSQL[@]}" "select count(*) from analytics.sessions where site_id = '${site_id}'"; }

# Prometheus answers in seconds; an empty answer means the series does not exist yet, which is not zero.
ms() {
  if [ -z "$1" ] || [ "$1" = "NaN" ]; then echo "-"; else
    python3 -c 'import sys; print(f"{float(sys.argv[1]) * 1000:.1f}")' "$1"
  fi
}

# Instant vector at a point in time, so the window is the measured phase and not "the last few minutes".
promql() {
  curl -fsS --data-urlencode "query=$1" --data-urlencode "time=$2" "${PROMETHEUS}/api/v1/query" |
    python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(r[0]["value"][1] if r else "")'
}

printf '\n%-7s %-9s %-9s %-8s %-7s %-7s %-9s %-9s %-7s %s\n' \
  rate accepted stored non202 k6_p95 k6_p99 svc_p95 svc_p99 drain_s sessions

for rate in "${RATES[@]}"; do
  before=$(stored)
  before_sessions=$(sessions)
  # Sessionization state and the realtime set are derived and rebuilt from the next event, so clearing them
  # costs nothing and makes every run start with fresh visitors instead of continuing the previous run's
  # sessions. The tracked-domain list and the daily salt live in the same Redis and are deliberately left
  # alone: losing the allow-list would have ingest reject everything until it restarts (ADR 0019).
  docker exec "${PROJECT}-redis-1" sh -c \
    'redis-cli --scan --pattern "session:*" | xargs -r redis-cli del >/dev/null; \
     redis-cli --scan --pattern "realtime:*" | xargs -r redis-cli del >/dev/null'

  RATE="$rate" DURATION="$DURATION" "${COMPOSE[@]}" run --rm \
    -e "RATE=$rate" -e "DURATION=$DURATION" k6 >"infra/k6/results/run-${rate}.log" 2>&1 || {
    echo "k6 exited non-zero at ${rate}/s — a threshold failed or the run was invalid; see the log." >&2
  }
  measure_end=$(date +%s)

  summary="infra/k6/results/write-path-${rate}rps.json"
  if [ ! -f "$summary" ]; then
    printf '%-7s %s\n' "$rate" "FAILED — k6 wrote no summary; it was killed or aborted. See results/run-${rate}.log"
    continue
  fi

  read -r accepted non202 k6_p95 k6_p99 dropped_iterations <<<"$(
    python3 - "$summary" <<'PY'
import json, sys
m = json.load(open(sys.argv[1]))["metrics"]
def v(name, key, default=0):
    return m.get(name, {}).get("values", {}).get(key, default)
# Every request the run made, warmup and the setup probe included: all of them become rows.
print(int(v("http_reqs", "count")),
      int(v("http_req_failed{phase:measure}", "passes")),
      round(v("http_req_duration{phase:measure}", "p(95)"), 1),
      round(v("http_req_duration{phase:measure}", "p(99)"), 1),
      int(v("dropped_iterations", "count")))
PY
  )"

  # Waits for the pipeline to catch up rather than assuming it has. The timeout is generous: a run that
  # cannot drain is itself the finding, and the row will show it as a shortfall instead of hanging for ever.
  target=$((before + accepted))
  drain_start=$(date +%s)
  until [ "$(stored)" -ge "$target" ] || [ $(($(date +%s) - drain_start)) -ge 300 ]; do sleep 2; done
  drain=$(($(date +%s) - drain_start))
  after=$(stored)

  window="${DURATION}"
  svc_p95=$(promql "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri=\"/api/event\"}[${window}])))" "$measure_end")
  svc_p99=$(promql "histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri=\"/api/event\"}[${window}])))" "$measure_end")

  printf '%-7s %-9s %-9s %-8s %-7s %-7s %-9s %-9s %-7s %s\n' \
    "$rate" "$accepted" "$((after - before))" "$non202" "$k6_p95" "$k6_p99" \
    "$(ms "$svc_p95")" "$(ms "$svc_p99")" "$drain" "$(($(sessions) - before_sessions))"

  if [ "$dropped_iterations" -ne 0 ]; then
    echo "  ! k6 dropped ${dropped_iterations} iterations at ${rate}/s: the generator could not offer the" >&2
    echo "    rate, so the latency above is not the latency at that rate. Give k6 more CPU and repeat." >&2
  fi
done

echo
echo "Rejections and drops over the whole series (both must be zero for the accounting above to mean anything):"
curl -fsS http://localhost:19081/actuator/prometheus | grep -E '^events_rejected_total' || echo "  events_rejected_total: no series — nothing was ever rejected"
curl -fsS http://localhost:19082/actuator/prometheus | grep -E '^events_dropped_total'
