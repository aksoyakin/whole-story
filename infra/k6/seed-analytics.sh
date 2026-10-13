#!/usr/bin/env bash
# Seeds the analytics corpus the read-side measurement needs (docs/performance.md).
#
#   infra/k6/seed-analytics.sh                      # the measured corpus: ~50M events over 24 months
#   infra/k6/seed-analytics.sh 2 200000 50000 1     # a small one, to check the shape before committing hours
#
# Arguments, in order: months, main-site events, events per neighbour site, neighbour count.
#
# psql runs inside the stack's own network and reaches Postgres by service name, so nothing depends on host
# networking or on a client being installed, and the client version matches the server.
#
# It connects as the superuser because it writes into analytics (processor's schema) and sites (api's schema)
# in one go. That is only acceptable for a measurement rig on a local machine: in production schema ownership
# is the boundary (D-012, D-037), and nothing here should ever be pointed at a real database.
set -euo pipefail

MONTHS=${1:-24}
MAIN_EVENTS=${2:-45000000}
NEIGHBOUR_EVENTS=${3:-2500000}
NEIGHBOURS=${4:-2}
MAIN_DOMAIN=${READPATH_DOMAIN:-readpath.example}

NETWORK=${SEED_NETWORK:-wholestory-readpath_internal}
HOST=${PGHOST:-postgres}
DB=${PGDATABASE:-wholestory}
USER=${PGUSER:-postgres}
# Matches POSTGRES_PASSWORD in loadtest.env, which this stack is started with.
export PGPASSWORD=${PGPASSWORD:-loadtest}

here=$(cd "$(dirname "$0")" && pwd)

cat <<EOF
Seeding $DB on $HOST (network $NETWORK)
  months            $MONTHS
  main site         $MAIN_EVENTS events  ($MAIN_DOMAIN)
  neighbours        $NEIGHBOURS x $NEIGHBOUR_EVENTS events
EOF

exec docker run --rm -i \
  --network "$NETWORK" \
  -e PGPASSWORD \
  -v "$here/seed-analytics.sql:/seed.sql:ro" \
  postgres:18.6-alpine \
  psql -h "$HOST" -U "$USER" -d "$DB" \
       -v ON_ERROR_STOP=1 \
       -v months="$MONTHS" \
       -v main_events="$MAIN_EVENTS" \
       -v neighbour_events="$NEIGHBOUR_EVENTS" \
       -v neighbours="$NEIGHBOURS" \
       -v main_domain="$MAIN_DOMAIN" \
       -f /seed.sql
