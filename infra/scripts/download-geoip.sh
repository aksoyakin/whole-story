#!/bin/sh
# Downloads the GeoLite2 City database for local development.
# Production keeps it current with the geoipupdate sidecar (see infra/docker-compose.prod.yml).
#
# Credentials are read from the repository's .env, which is never committed:
#   MAXMIND_ACCOUNT_ID=...
#   MAXMIND_LICENSE_KEY=...
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)

if [ ! -f "$ROOT/.env" ]; then
    echo "missing $ROOT/.env with MAXMIND_ACCOUNT_ID and MAXMIND_LICENSE_KEY" >&2
    exit 1
fi
. "$ROOT/.env"
: "${MAXMIND_ACCOUNT_ID:?set MAXMIND_ACCOUNT_ID in .env}"
: "${MAXMIND_LICENSE_KEY:?set MAXMIND_LICENSE_KEY in .env}"

DESTINATION="$ROOT/infra/geoip"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$DESTINATION"

curl -fsSL --retry 2 -u "$MAXMIND_ACCOUNT_ID:$MAXMIND_LICENSE_KEY" \
    "https://download.maxmind.com/geoip/databases/GeoLite2-City/download?suffix=tar.gz" \
    -o "$WORK/GeoLite2-City.tar.gz"
tar -xzf "$WORK/GeoLite2-City.tar.gz" -C "$WORK"
mv "$WORK"/GeoLite2-City_*/GeoLite2-City.mmdb "$DESTINATION/GeoLite2-City.mmdb"

echo "Downloaded $DESTINATION/GeoLite2-City.mmdb"
echo "Run ingest with: GEOIP_DATABASE=$DESTINATION/GeoLite2-City.mmdb"
