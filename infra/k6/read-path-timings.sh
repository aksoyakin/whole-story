#!/usr/bin/env bash
# Median timings for the dashboard's queries over one period (docs/performance.md).
#
#   infra/k6/read-path-timings.sh 30d              # production's settings
#   WORK_MEM=64MB infra/k6/read-path-timings.sh 30d
#   REPS=9 infra/k6/read-path-timings.sh 12m
#
# Each statement runs REPS times and the median is the number, with min and max alongside. A single run is not
# a measurement on this corpus: back to back, one of these statements came out at 2216, 3521 and 2348 ms, so
# the spread between runs was wider than the differences being looked for.
set -euo pipefail

PERIOD=${1:-30d}
REPS=${REPS:-5}
WORK_MEM=${WORK_MEM:-}
NETWORK=${SEED_NETWORK:-wholestory-readpath_internal}
HOST=${PGHOST:-postgres}
DB=${PGDATABASE:-wholestory}
USER=${PGUSER:-postgres}
export PGPASSWORD=${PGPASSWORD:-loadtest}
SITE=${SITE:-00000000-0000-7000-9000-000000000000}

case "$PERIOD" in
  today) FROM="date_trunc('day', now())" ;;
  7d)    FROM="date_trunc('day', now()) - interval '6 days'" ;;
  30d)   FROM="date_trunc('day', now()) - interval '29 days'" ;;
  12m)   FROM="date_trunc('day', now()) - interval '12 months'" ;;
  24m)   FROM="date_trunc('day', now()) - interval '24 months'" ;;
  *)     echo "unknown period: $PERIOD (today, 7d, 30d, 12m, 24m)" >&2; exit 2 ;;
esac

here=$(cd "$(dirname "$0")" && pwd)

psql_in_stack() {
  docker run --rm -i --network "$NETWORK" -e PGPASSWORD \
    -v "$here/read-path-timings.sql:/timings.sql:ro" \
    postgres:18.6-alpine psql -h "$HOST" -U "$USER" -d "$DB" "$@"
}

# Resolved once, so every repetition covers exactly the same window.
bounds=$(psql_in_stack -At -F'|' \
  -c "select ($FROM)::timestamptz, (date_trunc('day', now()) + interval '1 day')::timestamptz")

echo "period    $PERIOD"
echo "reps      $REPS"
echo "work_mem  ${WORK_MEM:-production default (4MB)}"
echo "window    ${bounds%%|*}  ..  ${bounds##*|}"
echo

# The SET and the file run in one session, in this order, so the setting applies to every statement below.
psql_in_stack \
  -v ON_ERROR_STOP=1 \
  -v site="$SITE" \
  -v from="${bounds%%|*}" \
  -v to="${bounds##*|}" \
  -v reps="$REPS" \
  ${WORK_MEM:+-c "set work_mem = '$WORK_MEM'"} \
  -f /timings.sql
