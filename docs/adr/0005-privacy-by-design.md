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
Fully implemented: hashing, the daily salt, discarding the IP, the GeoIP lookup
(see [ADR 0013](0013-geoip-lookups-and-database-distribution.md)) and the rotation grace period.

The grace period needed a correction to hold its promise. Salts were given a 25 hour time-to-live counted from
first use, but they are created lazily: a salt first needed at 14:00 survived until 15:00 the next day, roughly a
full day after the grace period ended, and yesterday's salt being alive is exactly what makes cross-day linking
possible. A salt now expires at an absolute deadline, 30 minutes into the following day. The previous day's salt is
also only ever read, never created, so a day without traffic cannot be given a salt afterwards.

Measured in production before the fix, at 18:10 UTC: the previous day's salt still had about two hours left to
live, eighteen hours after the grace period should have removed it, and the current day's salt was set to outlive
its deadline by seventeen hours. For that whole day, linking a visitor across the midnight boundary was possible.

## Consequences
- No IP address reaches Kafka, logs or the database. An integration test asserts that the published message does
  not contain the client IP; a Playwright test asserts that no cookie or storage entry is created.
- The same person is a new visitor on a new day, by design.
- 64-bit hashes make `COUNT(DISTINCT)` cheap; collisions per site and day are negligible.
