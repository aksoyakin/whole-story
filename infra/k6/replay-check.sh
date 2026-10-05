#!/usr/bin/env bash
# Re-consumes the whole raw-events topic and checks that nothing changed.
#
# This is the experiment behind ADR 0006. Delivery is at-least-once, and what makes that safe is that the
# write is idempotent: events are inserted with ON CONFLICT DO NOTHING RETURNING, and sessions and rollups are
# derived only from the rows that insert actually created (D-018, D-032). The claim is therefore testable in
# one move — rewind the consumer group to the beginning, let it read everything a second time, and compare.
#
# It cannot be tested from the HTTP side: the event id is minted by ingest, so posting the same payload twice
# is two legitimate events, not a duplicate. The duplicate this design defends against is a redelivery from
# Kafka, which is what a rewind reproduces.
#
# It also reads the realtime counter before and after, because that number is written outside the transaction
# and from every event in the batch rather than from the inserted ones (D-140).
#
# Usage, from the repository root, against the stack in docs/performance.md:
#   infra/k6/replay-check.sh
set -euo pipefail

cd "$(dirname "$0")/../.."

DOMAIN="${LOADTEST_DOMAIN:-loadtest.example}"
PROJECT="wholestory-loadtest"
COMPOSE=(docker compose -p "$PROJECT" --env-file infra/k6/loadtest.env
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.loadtest.yml)
PSQL=(docker exec "${PROJECT}-postgres-1" psql -U postgres -d wholestory -At -c)
KAFKA=(docker exec "${PROJECT}-kafka-1" /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092)

site_id=$("${PSQL[@]}" "select id from sites.sites where domain = '${DOMAIN}' and deleted_at is null")
[ -n "$site_id" ] || { echo "No live site for ${DOMAIN}" >&2; exit 1; }

counts() {
  "${PSQL[@]}" "
    select (select count(*) from analytics.events   where site_id = '${site_id}')
        || ' ' || (select count(*) from analytics.sessions where site_id = '${site_id}')
        || ' ' || (select coalesce(sum(pageviews), 0) from analytics.page_hourly where site_id = '${site_id}')
        || ' ' || (select coalesce(sum(count), 0) from analytics.custom_event_hourly where site_id = '${site_id}')"
}
realtime() { docker exec "${PROJECT}-redis-1" redis-cli ZCARD "realtime:${site_id}"; }
lag() {
  "${KAFKA[@]}" --group processor --describe 2>/dev/null |
    awk '$1 == "processor" && $6 ~ /^[0-9]+$/ { total += $6 } END { print total + 0 }'
}

read -r events_before sessions_before pages_before customs_before <<<"$(counts)"
echo "before   events=${events_before} sessions=${sessions_before} page_hourly=${pages_before} custom_hourly=${customs_before}"

# Cleared so the effect of the replay on this number is visible rather than hidden under the traffic that
# just ran. It is derived state with a ten minute life, so clearing it costs nothing.
docker exec "${PROJECT}-redis-1" redis-cli DEL "realtime:${site_id}" >/dev/null
echo "realtime cleared, now $(realtime)"

# The group has to have no members before its offsets can be moved.
echo "stopping the processor"
"${COMPOSE[@]}" stop processor >/dev/null 2>&1
"${KAFKA[@]}" --group processor --topic raw-events --reset-offsets --to-earliest --execute >/dev/null
echo "offsets rewound to the beginning of raw-events"

"${COMPOSE[@]}" start processor >/dev/null 2>&1
echo "processor restarted; waiting for it to read the topic again"

started=$(date +%s)
# A rewound group reports no lag until it has joined and fetched, so a zero in the first seconds means
# "not started yet" rather than "finished": wait for lag to appear before waiting for it to go away.
until [ "$(lag)" -gt 0 ] || [ $(($(date +%s) - started)) -ge 120 ]; do sleep 2; done
peak=$(lag)
echo "lag after rejoining: ${peak}"
until [ "$(lag)" -eq 0 ] || [ $(($(date +%s) - started)) -ge 3600 ]; do sleep 5; done
elapsed=$(($(date +%s) - started))

read -r events_after sessions_after pages_after customs_after <<<"$(counts)"
echo "after    events=${events_after} sessions=${sessions_after} page_hourly=${pages_after} custom_hourly=${customs_after}"
echo "replayed ${peak} records in ${elapsed}s"
echo "realtime after the replay: $(realtime)"

if [ "$events_before" = "$events_after" ] && [ "$sessions_before" = "$sessions_after" ] &&
  [ "$pages_before" = "$pages_after" ] && [ "$customs_before" = "$customs_after" ]; then
  echo "PASS — reading the whole topic a second time changed nothing (ADR 0006)"
else
  echo "FAIL — the second pass changed the stored data; idempotency is not holding" >&2
  exit 1
fi
