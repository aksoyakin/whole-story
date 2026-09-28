# 0013. GeoIP lookups and database distribution

- Status: Accepted
- Date: 2026-09-28

## Context
Events need a country, region and city, and [ADR 0005](0005-privacy-by-design.md) requires the lookup to happen in
`ingest`, before the IP address is discarded. That means every ingest instance needs a local IP-to-location database.

MaxMind's GeoLite2 City database is free but licensed: it may not be redistributed, and the licence requires running a
reasonably current copy. MaxMind publishes a new build twice a week, so the file cannot simply be committed or frozen
into a container image and forgotten.

## Decision
- Lookups use the official `com.maxmind.geoip2:geoip2` reader against **GeoLite2 City**, opened memory-mapped so the
  60+ MB database stays off the JVM heap, with a node cache in front of it.
- The database file is **not** part of the application image. In production a `maxmindinc/geoipupdate` sidecar
  downloads it into a Docker volume and refreshes it every 72 hours; `ingest` mounts that volume read-only and
  reloads the file when its modification time changes. Locally, `infra/scripts/download-geoip.sh` fetches the same
  database using credentials from an uncommitted `.env`.
- A missing, stale or corrupt database is **never fatal**: `ingest` starts, keeps accepting events and stores them
  without a location. This is reported through the `geoip.database.loaded` and `geoip.database.age` metrics rather
  than the health endpoint, because failing the health check would restart a container that is serving traffic
  correctly.
- Only three fields are kept: country (ISO 3166-1 alpha-2), region and city geoname id. Coordinates and postal codes
  are never read. The region is stored as a full ISO 3166-2 code (`TR-06`, `US-WA`), because subdivision codes are
  not unique on their own — `E` is a Swedish county. The least specific subdivision is used, which is the same
  administrative level across countries: a Turkish province, a US state, a UK country.
- Private, loopback and link-local addresses are not looked up. Addresses are parsed with `InetAddress.ofLiteral`,
  which cannot trigger a DNS resolution: the value originates from an attacker-controlled `X-Forwarded-For` header.

## Consequences
- The licence is satisfied without redistributing the database, and the image stays small; the trade-off is one more
  container and a volume shared between the sidecar and every ingest instance on the host.
- A geo lookup never fails an event, so a sidecar outage degrades data quality instead of losing data.
- Lookup failures are logged by exception type only. GeoIP2 puts the queried address into some exception messages,
  and no code path may write a raw IP to the logs.
- `geoip2` brings Jackson 2 with it, while Spring Boot 4 uses Jackson 3. The two live under different root packages
  (`com.fasterxml.jackson` and `tools.jackson`) and do not conflict; the cost is about 2 MB in the ingest image.
- Tests run against MaxMind's own test database, which holds fabricated records and is published under
  Apache-2.0/MIT, so geo resolution is covered deterministically in CI without a licence key.
