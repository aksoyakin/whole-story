#!/usr/bin/env bash
# Times the dashboard's queries over one period, against the seeded corpus (docs/performance.md).
#
#   infra/k6/read-path-queries.sh 30d          # the period a dashboard defaults to
#   infra/k6/read-path-queries.sh 7d
#   infra/k6/read-path-queries.sh today
#   infra/k6/read-path-queries.sh 12m          # a custom range, which is where the shapes stop holding
#
# Each period is run twice. The first pass reads what is not in cache yet and the second reads a warm one, and
# both are reported: a dashboard somebody opens for the first time after a deploy sees the former, and the
# claim would be dishonest with only the latter. Postgres is left untuned because production is (128 MB of
# shared_buffers, 4 MB of work_mem), so the gap between the two passes is itself part of the result.
set -euo pipefail

PERIOD=${1:-30d}
NETWORK=${SEED_NETWORK:-wholestory-readpath_internal}
HOST=${PGHOST:-postgres}
DB=${PGDATABASE:-wholestory}
USER=${PGUSER:-postgres}
export PGPASSWORD=${PGPASSWORD:-loadtest}

# The main site seed-analytics.sql creates.
SITE=${SITE:-00000000-0000-7000-9000-000000000000}

# Days are cut in the site's timezone, which is what the api does (ADR 0020); the bounds are handed to psql
# already resolved so that the statements stay the ones JdbcStatsQueries sends.
case "$PERIOD" in
  today) FROM="date_trunc('day', now())" ;;
  7d)    FROM="date_trunc('day', now()) - interval '6 days'" ;;
  30d)   FROM="date_trunc('day', now()) - interval '29 days'" ;;
  12m)   FROM="date_trunc('day', now()) - interval '12 months'" ;;
  24m)   FROM="date_trunc('day', now()) - interval '24 months'" ;;
  *)     echo "unknown period: $PERIOD (today, 7d, 30d, 12m, 24m)" >&2; exit 2 ;;
esac

here=$(cd "$(dirname "$0")" && pwd)

# The bounds have to be real timestamps rather than SQL expressions, because they appear in the statements as
# parameters. Resolved here, once, so that both passes cover exactly the same window.
bounds=$(docker run --rm --network "$NETWORK" -e PGPASSWORD postgres:18.6-alpine \
  psql -h "$HOST" -U "$USER" -d "$DB" -At -F'|' \
       -c "select ($FROM)::timestamptz, (date_trunc('day', now()) + interval '1 day')::timestamptz")
FROM_TS=${bounds%%|*}
TO_TS=${bounds##*|}

echo "period   $PERIOD"
echo "site     $SITE"
echo "window   $FROM_TS  ..  $TO_TS"
echo

for pass in cold warm; do
  echo "############################## $pass pass ($PERIOD)"
  docker run --rm -i \
    --network "$NETWORK" \
    -e PGPASSWORD \
    -v "$here/read-path-queries.sql:/queries.sql:ro" \
    postgres:18.6-alpine \
    psql -h "$HOST" -U "$USER" -d "$DB" \
         -v ON_ERROR_STOP=1 \
         -v site="$SITE" \
         -v from="$FROM_TS" \
         -v to="$TO_TS" \
         -f /queries.sql
  echo
done
