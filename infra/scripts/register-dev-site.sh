#!/bin/sh
# Walking-skeleton helper until Site Management publishes SiteRegistered events (M3):
# registers a domain -> siteId mapping in the ingest allow-list.
# Usage: infra/scripts/register-dev-site.sh <domain> [siteId]
set -e
DOMAIN="${1:?usage: $0 <domain> [siteId]}"
SITE_ID="${2:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
docker compose -f "$(dirname "$0")/../docker-compose.yml" exec -T redis redis-cli HSET sites:domains "$DOMAIN" "$SITE_ID" >/dev/null
echo "$DOMAIN -> $SITE_ID"
