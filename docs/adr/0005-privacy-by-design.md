# 0005. Privacy by design: the IP address never leaves ingest

- Status: Accepted
- Date: 2026-09-26

## Context
The product promise is analytics without cookies, personal data or stored IP addresses. Visitors still need to be
counted once per day, and events need a country.

## Decision
- The client IP is used in `ingest` only, for two things, and then discarded:
  1. `visitorHash = SHA-256(dailySalt + siteId + ip + userAgent)`, truncated to 64 bits (stored as `bigint`).
  2. GeoIP lookup (country, region, city).
- The daily salt is random, shared through Redis and expires on its own; it rotates at 00:00 UTC. For 30 minutes
  after rotation, ingest also computes the hash with the previous salt so that sessions crossing midnight are not
  split; after that, the previous salt is gone.
- Event time is assigned by ingest's clock; the browser clock is not trusted.
- The tracker stores nothing in the browser.

## Implementation status
Hashing, the daily salt, discarding the IP and the GeoIP lookup (see [ADR 0013](0013-geoip-lookups-and-database-distribution.md))
are implemented. The 30 minute rotation grace period is still open: `ingest` does not yet compute the previous
salt's hash, although the processor already continues sessions from the previous hash when it is present.

## Consequences
- No IP address reaches Kafka, logs or the database. An integration test asserts that the published message does
  not contain the client IP; a Playwright test asserts that no cookie or storage entry is created.
- The same person is a new visitor on a new day, by design.
- 64-bit hashes make `COUNT(DISTINCT)` cheap; collisions per site and day are negligible.
