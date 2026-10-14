#!/usr/bin/env bash
# Fills analytics.visitor_daily over every finished UTC day the corpus holds, for the before-and-after in
# docs/performance.md.
#
#   infra/k6/backfill-visitor-daily.sh
#
# The statement is not written here. It is extracted from VisitorDailyRollup at run time, so what this
# measures is the statement the service actually runs and the two cannot drift apart — a copy would be a
# third definition of the rollup, and the one nobody updates.
#
# The service paces itself at MAX_DAYS_PER_RUN days every ten minutes, so a two-year backfill takes hours of
# wall clock there. Here the same statements run back to back, which times the work rather than the schedule.
set -euo pipefail

NETWORK=${SEED_NETWORK:-wholestory-readpath_internal}
HOST=${PGHOST:-postgres}
DB=${PGDATABASE:-wholestory}
USER=${PGUSER:-postgres}
export PGPASSWORD=${PGPASSWORD:-loadtest}

here=$(cd "$(dirname "$0")" && pwd)
source=$here/../../services/processor/src/main/java/world/wholestory/processor/rollup/VisitorDailyRollup.java
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

python3 - "$source" "$work/backfill.sql" <<'PY'
import re, sys

source, target = sys.argv[1], sys.argv[2]
java = open(source, encoding="utf-8").read()
# Every statement the rollup runs for one day, not just the first: the page dimension is computed by a
# second one, and a backfill that extracted only the first would leave the page rows out while moving the
# watermark past them -- which is the exact silent shortfall V5 exists to undo.
names = ["COMPUTE", "COMPUTE_PAGES"]
bodies = []
for name in names:
    found = re.search(r'private static final String %s = """\n(.*?)\n            """;' % name, java, re.S)
    if not found:
        raise SystemExit("%s not found in %s; the rollup's SQL has moved" % (name, source))
    sql = "\n".join(l[12:] if l.startswith(" " * 12) else l for l in found.group(1).split("\n"))
    sql = sql.replace(":dayStart", "d::timestamptz").replace(":dayEnd", "(d + 1)::timestamptz").replace(":day", "d")
    bodies.append("\n".join(("        " + l) if l.strip() else l for l in sql.split("\n")) + ";")
body = "\n".join(bodies)

open(target, "w", encoding="utf-8").write("""\\timing on
do $backfill$
declare
    d date;
    first_day date;
    last_day date := (now() at time zone 'UTC')::date - 1;
    done int := 0;
begin
    select min(started_at at time zone 'UTC')::date into first_day from analytics.sessions;
    if first_day is null then
        raise exception 'no sessions to roll up; seed the corpus first';
    end if;
    raise notice 'backfilling % .. %', first_day, last_day;
    for d in select generate_series(first_day, last_day, interval '1 day')::date loop
__COMPUTE__
        done := done + 1;
    end loop;
    update analytics.visitor_daily_watermark set covered_through = last_day, updated_at = now();
    raise notice 'backfill complete: % days, through %', done, last_day;
end
$backfill$;
""".replace("__COMPUTE__", body))
PY

docker run --rm -i --network "$NETWORK" -e PGPASSWORD \
  -v "$work/backfill.sql:/backfill.sql:ro" \
  postgres:18.6-alpine \
  psql -h "$HOST" -U "$USER" -d "$DB" -v ON_ERROR_STOP=1 -f /backfill.sql
