# 0007. Events, sessions and additive-only rollups

- Status: Accepted
- Date: 2026-09-26

## Context
Pre-aggregating everything into hourly rollups is the obvious way to make dashboards fast, but unique visitors
are not additive: 100 visitors at 14:00 and 100 at 15:00 are not 200 visitors, because they may be the same people.
Summing them would silently produce wrong numbers.

## Decision
- `analytics.events`: one row per event, monthly range partitions.
- `analytics.sessions`: one row per session (entry and exit page, page count, duration, bounce, source, geo,
  device), upserted per batch, monthly range partitions by start time.
- Rollups only for additive metrics: `page_hourly` (pageviews per path) and `custom_event_hourly`.
- Unique visitors, visits, bounce rate and duration are computed exactly from `sessions`, which is several times
  smaller than `events`.
- Rollups are hourly in UTC; a day in the site's timezone is aggregated at query time.
- Partitions are created ahead of time by a daily job (no `pg_partman`, no DEFAULT partition); raw data is kept
  24 months, rollups indefinitely.

## Implementation status
Tables, partition creation and rollups are implemented. Dropping expired partitions and alerting on missing
partitions are planned.

The bounce flag is no longer stored on the session row: its definition moved into the `api_sessions` view, so that
redefining it does not rewrite partitions and applies to history as well. See
[ADR 0017](0017-bounce-definition.md).

## Consequences
- Numbers are exact. If load tests show the exact distinct counts are too slow, HyperLogLog sketches are the
  planned next step, adopted on evidence rather than up front.
- A missing partition makes inserts fail, so partition creation is monitored.
