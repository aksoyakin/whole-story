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

One limitation came with counting a day once: an outage longer than that hour means sessions arrive for a
day already counted, and they were never picked up, so reports over a range containing that day were short
by them. The gauge saying how far behind the rollup is did not show it either, because the rollup was not
behind — it was wrong, which is worse.

**That is now built, and the shape of the fix is the interesting part.** A day can be counted again: the
writer marks a day whenever it stores a batch belonging to one the watermark already claims, in the same
transaction as the rows themselves, and the job recomputes marked days with an upsert rather than skipping
them on conflict. In healthy operation nothing is ever marked — a batch belongs to today, and today is
always beyond the watermark — so the mechanism costs a predicate that matches nothing.

Three things about it are worth stating, because each was a choice rather than a detail:

- **The grace period changed meaning rather than becoming redundant.** It used to be the only defence, so it
  had to be long enough to cover every late arrival. Now it keeps the common case to a single computation:
  without it, the visits still being extended between midnight and 00:30 would mark every single day for
  recomputation. Being generous with it is cheap; being wrong with it is no longer permanent.
- **Marks are per day, not per site.** A site filter would need a second variant of both compute statements —
  a third definition of the rollup, and the one nobody updates. A day's recompute is the work the job does
  for every day anyway.
- **The job clears a mark by matching the mark it read, not by day.** A batch landing while a day is being
  recomputed may be outside that recompute's snapshot, so its mark has to survive; a re-mark changes the
  value and the delete finds nothing. The marks are sequence numbers rather than timestamps so that this is
  exact rather than a question about clock resolution.

What it does not do is make a report correct the instant a late batch lands: a marked day is still answered
from the rollup until the job's next run. That window is bounded by the run interval, where before it was
bounded by nothing.

**A second defect turned up next to it, and it is the same family.** Removing a site purges the analytics
schema (ADR 0023), and `visitor_daily` was never added to the list of tables it purges — so for three rounds
a removed site kept a row per day per dimension value while every check said its data was gone. Nothing
caught it: the integration test counted exactly the tables the purger names, so it asserted the
implementation rather than the promise, and the live verification query was written from the same list. Both
now name every table keyed by site, and the test fails if the purger forgets one.

Measured at render level: a render over a twelve-month range went from 70 s to 40 s and a thirty-day one
from 7.2 s to 4.3 s, with the endpoints it answers between 72% and 99.8% faster and no longer growing with the range. It
costs 253 MB against the sessions' 5 GB, and about 2% on the reports it does not serve, which now compete
with it for cache. See `docs/performance.md`.

**The page joined it afterwards**, which is a correction to the paragraph above rather than an exception to
it. The rule was "not the high cardinality ones"; the measured rule is "not a *new* high cardinality axis".
The pathname axis is already kept hourly in `page_hourly`, so a daily grain is a tenth of a rollup this
schema carries anyway — 11,143,450 rows against 1,040,394 on the same corpus. It is also the first dimension
rolled up from the events rather than the sessions, so the two partial days at the edges of a range are
counted from the events too. A render over a twelve-month range went from 46.9 s to 24.4 s with it, the page
breakdown itself from 18.9 s to 0.13 s, while the filtered control arm stayed put. Twelve months is a range
the api serves and the dashboard does not currently offer — see `docs/performance.md` for why the figure is
still the one worth quoting.

**The entry and exit pages followed, and they are the cheapest case rather than another exception.** The rule
had now been corrected twice, so it is worth stating in its final form: what keeps a dimension out is a *new*
axis of cardinality, not cardinality itself. These two are the pathname axis for the third time, seen from
the visit instead of the pageview — and because an entry page is a column on the session, they enter as two
more grouping sets in the statement that already reads the day rather than as another pass over it. They are
also the first rolled up dimensions to carry every column: visits, bounces and a duration sum are questions a
page cannot be asked and a visit answers directly, so leaving them null as `PAGE` does would have meant a
second pass over the sessions to store less. Measured at render level: a twelve-month render went from 26.2 s
to 17.2 s, the two cards from 3.8 s and 4.0 s to 148 ms and 62 ms, while the table went from 460 MB to 887 MB
and a full 712-day backfill from 156 s to 236 s. Exactness was checked rather than argued, which matters
because for these two the premise holds *by construction* — a session row has one `started_at` — and the
round before this one is the reason an argument is no longer accepted in place of a figure: over a 365-day
window the rollup's sums, the same days counted live and one count over the whole window agree to the unit
for every busy entry and exit page. The entry figures also differ from the exit ones, which is what proves
the two grouping sets did not end up swapped — a failure that would have produced a perfectly plausible
dashboard.

Migrating it is one statement and no DDL: the watermark goes back to the beginning. That is also what makes
the deploy safe in the only direction that matters — a null watermark means nothing at all is read from the
rollup (`Coverage`), so every dimension is counted live until the job has rebuilt, and api does not start
until processor is healthy and its migrations have run. The transition is slower, never wrong.

That brought out a dependency worth stating plainly, because it is the premise this whole amendment rests
on and it is enforced somewhere else entirely: **a `visitor_hash` must belong to exactly one UTC day.** For
sessions that is free — a row has one `started_at`. For events it is true only because ingest computes the
hash per event from that day's salt and `EventWriter` stores that event's own hash, with `previousVisitorHash`
keeping the visit together across the boundary (D-028). Any future rollup counted from events inherits that
dependency. The seeded corpus does not satisfy it, which is how it was found: it gives every event of a visit
one hash, and the page figures came out 0.06% high.

## Amendment: expiring the raw data turned out to change what the api may answer

The decision above says raw data is kept 24 months. Only half of it was built — a job creates monthly
partitions ahead of time — and the other half lived in the status note below, as "planned", from the day
this was written. Building it is where the interesting part is, because dropping an old month is not only a disk operation: it narrows what
this service can honestly be asked.

A report is not answered from one place. The interior UTC days of a range come from `visitor_daily`, which is
kept indefinitely, while the two partial days at its edges are counted live from the sessions or the events,
because a site's day is cut in its own timezone and so a range almost never begins on a UTC midnight. Drop
the raw data under an edge and that edge counts zero: a thirty-day report reaching just past the horizon
comes back about a day short, silently. A *filtered* report comes back empty, because a filter cannot be
answered from the rollup at all, while the summary's pageview figure stays where it was, since `page_hourly`
is also kept forever. Every number on the page would have looked plausible and the page would have
contradicted itself.

So the horizon is published rather than kept inside the service that enforces it. After dropping, partition
maintenance records the first day the raw data still covers, in a one-row table with a view of its own
(`api_raw_data_retention`, the same shape as the rollup's watermark), and api refuses a range that begins
before it. Refusing rather than shortening is the choice [ADR 0020](0020-reporting-queries.md) already made
for the ceiling on how long a range may be, for the same reason: somebody asked for a specific window.

Four things about it are decisions rather than details:

- **The published horizon comes from the catalogue, not from the constant.** It is the lower bound of the
  oldest partition that actually survived. The two differ in every case that matters — before anything has
  ever been dropped, on a database younger than the horizon, and on one carrying a partition the job declined
  to recognise — and the difference is always in the safe direction: api cannot promise history that is gone.
  It also means a shorter retention could never make a report wrong, only make reports reach less far back,
  which is why the relationship between the retention and the 366-day ceiling needs no guard.
- **What may be dropped is chosen from the catalogue too, with the bounds read rather than assumed.** A loop
  over generated partition names can only find what it already assumed: it would keep a month attached under
  an unexpected name forever, and it would aim an irreversible statement at whatever answered to the name it
  expected. The bounds are extracted in SQL and cast back to `timestamptz` instead of compared as text,
  because the literal `pg_get_expr` prints is rendered in the session's timezone while the literal carries
  its own offset. Anything that is not a plain range — a DEFAULT, an open-ended bound — is left alone and
  logged.
- **A month goes when all of it is older than the horizon, never when part of it is**, which makes the window
  24 to 25 months rather than exactly 24. The implementation is the generous direction, and it is worth
  stating rather than rounding away.
- **The floor is compared as an instant, not as a date.** The horizon is a UTC day; a range starts at the
  site's local midnight. In Istanbul a range beginning on the first retained day starts three hours before
  the raw data does, and those three hours are precisely the edge that is counted live. A date comparison
  would have accepted it.

Two consequences are honest to write down. The first is that this cannot be verified in production for two
years: the database was created in October 2026, so nothing in it becomes droppable until late 2028. The
proof is an integration test that puts partitions on either side of a boundary two years wide, and that was
watched failing with the drop removed. The second is that the deploy order matters once, in the direction
compose already handles — api reads a view processor's migration creates, and api does not start until
processor is healthy and its migrations have run.

What is still missing is the other half of the status note: nothing yet reports how far ahead the partitions
reach, and a missing partition fails an insert outright.

## Implementation status
Tables, partition creation, the rollups and the expiry of old partitions are implemented. Alerting on
missing partitions is still planned.

The bounce flag is no longer stored on the session row: its definition moved into the `api_sessions` view, so that
redefining it does not rewrite partitions and applies to history as well. See
[ADR 0017](0017-bounce-definition.md).

## Consequences
- Numbers are exact. If load tests show the exact distinct counts are too slow, HyperLogLog sketches are the
  planned next step, adopted on evidence rather than up front.
- A missing partition makes inserts fail, so partition creation is monitored.
