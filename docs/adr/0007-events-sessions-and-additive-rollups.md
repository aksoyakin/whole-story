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

## Amendment: visitors are additive across days, and that was not noticed here

Measured while timing the read path. The premise above is right about hours and wrong about days, and the
reason is in another decision: [ADR 0005](0005-privacy-by-design.md) rotates the hashing salt every UTC day,
so the same person is a different `visitor_hash` tomorrow. Two different days therefore hold disjoint sets of
hashes, and a distinct count over a range equals the sum of the daily distinct counts — exactly, not
approximately. On the seeded corpus a thirty-day live count of 54,176 was matched by the sum of its UTC days
to the unit, and per country, across 193 of them, the difference was zero.

The two ADRs interact and neither said so. It matters because it is the difference between "no rollup can
hold unique visitors" and "a rollup at the salt's own grain holds them exactly", which is a much cheaper
answer than the HyperLogLog this ADR reaches for.

Two conditions come with it, and both are sharp:

- **The grain must be the UTC day**, because that is the salt's grain. An hourly rollup would double count
  anyone active in two hours of the same day, and a rollup keyed on the *site's* local day overcounted by 16%
  on the same corpus, since one UTC day straddles two local ones.
- **A site's day does not align with it.** Days are cut in the site's timezone (ADR 0020), so the range a
  dashboard asks for starts and ends partway through a UTC day. Summing whole overlapping days was 1.7%
  wrong — small, and silent. Taking the interior days from the rollup and counting the two edge days live
  reproduced the live figure exactly.

Built, in `analytics.visitor_daily`: one row per site, per UTC day, per dimension value, with a watermark row
saying how far it is complete and a reader that takes the interior days from it and counts the two partial
days at the edges live. It holds seven of the session-backed dimensions and not the high cardinality ones —
an entry page or a campaign per day would make it a second copy of the sessions rather than a summary.

It is an accelerator and never an authority: a day it has not reached is counted the way it always was, so
being behind costs speed and not correctness. A day is counted an hour after it ends rather than at midnight,
because it is counted once and never recounted, and a session that began at 23:58 is still being extended
after midnight.

One limitation comes with counting a day once: an outage longer than that hour means sessions arrive for a
day already counted, and they are never picked up, so reports over a range containing that day are short by
them. The remedy — marking a day dirty when a batch lands behind it and recomputing it — is not built, and
the gauge saying how far behind the rollup is will not show this, because the rollup is not behind.

Measured at render level: a twelve-month dashboard went from 70 s to 40 s and a thirty-day one from 7.2 s to
4.3 s, with the endpoints it answers between 72% and 99.8% faster and no longer growing with the range. It
costs 253 MB against the sessions' 5 GB, and about 2% on the reports it does not serve, which now compete
with it for cache. See `docs/performance.md`.

**The page joined it afterwards**, which is a correction to the paragraph above rather than an exception to
it. The rule was "not the high cardinality ones"; the measured rule is "not a *new* high cardinality axis".
The pathname axis is already kept hourly in `page_hourly`, so a daily grain is a tenth of a rollup this
schema carries anyway — 11,143,450 rows against 1,040,394 on the same corpus. It is also the first dimension
rolled up from the events rather than the sessions, so the two partial days at the edges of a range are
counted from the events too. A twelve-month dashboard went from 46.9 s to 24.4 s with it, the page breakdown
itself from 18.9 s to 0.13 s, while the filtered control arm stayed put.

That brought out a dependency worth stating plainly, because it is the premise this whole amendment rests
on and it is enforced somewhere else entirely: **a `visitor_hash` must belong to exactly one UTC day.** For
sessions that is free — a row has one `started_at`. For events it is true only because ingest computes the
hash per event from that day's salt and `EventWriter` stores that event's own hash, with `previousVisitorHash`
keeping the visit together across the boundary (D-028). Any future rollup counted from events inherits that
dependency. The seeded corpus does not satisfy it, which is how it was found: it gives every event of a visit
one hash, and the page figures came out 0.06% high.

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
