# Performance

Measured numbers, and how to get them again. Nothing here is estimated: every figure below came out of a run
that can be repeated with the scripts in this repository.

Two halves, measured separately because they fail differently. The write path is about keeping up with
traffic, and it does. The read path is about answering a question about history, and that is where the numbers
stop being comfortable.

## What was measured, part one: the write path

`tracker → ingest → Kafka → processor → PostgreSQL`. Throughput on its own would be a weak claim, so each run
answers four questions at once:

1. How many events a second does ingest accept, and how long does a request take?
2. Does the processor keep up, or does a backlog build?
3. Does everything that was accepted end up stored?
4. Does reading the topic a second time change any of the stored numbers (ADR 0006)?

Dashboard query latency is the second half, further down. It depends almost entirely on how much data the
database holds, so it needed a corpus seeded first.

## The machine

| | |
|---|---|
| Host | Apple M4, 10 cores, 16 GB, macOS 26.5.2 |
| Docker | Docker Desktop 29.8.0; the VM was given 10 CPUs and 7.75 GiB |
| Stack | `infra/docker-compose.prod.yml` — the production file, so the services ran with the env, the dependency order and the JVM flags they run with in production |
| Images | built locally from `infra/docker/service.Dockerfile`, the same file CI uses |
| Generator | k6 1.3.0 in a container on the stack's own network, capped at 4 CPUs and 1 GiB |

**These numbers are a floor, not a capacity, and they are not the production server's.** Five things differ
from production and all of them are visible in `infra/k6/docker-compose.loadtest.yml`:

- **Architecture.** Production images are `linux/amd64` (D-055) and this host is Apple Silicon, where they
  would only run under emulation — which measures the emulator. The images were therefore built for arm64 from
  the same Dockerfile; the artifact differs only in architecture.
- **Memory limits.** Production sets none, so a JVM with `MaxRAMPercentage=75` sizes its heap against the
  whole machine. Three of those on an 8 GiB VM is a run that ends in `ExitOnOutOfMemoryError`, so each service
  was given a ceiling. Worth naming because the ceilings are mine and not the product's: api, idle throughout,
  sat at 494 MiB of the 768 MiB it was given.
- **No reverse proxy.** Traefik is not in the path, so the request latency below is the
  service's and not a visitor's. It is also what lets one generator stand in for thousands of visitors: ingest
  takes the client address from `X-Forwarded-For`, and with nothing in front, the generator sets it. Through
  the production path it cannot — an event sent there with an invented address was counted and located under
  the real one (ADR 0016).
- **The generator shares the machine.** It took about 1.5 cores of the 10 at the highest rate.
- **Docker Desktop's file I/O** is slower than a Linux host's, which is a penalty on PostgreSQL and Kafka.

## Results: the write path

Each run holds one fixed arrival rate for two minutes, after a 30 second warmup at a tenth of it. A series of
plateaus rather than one ramp: in a ramp the rate and the latency move together, so the result cannot answer
"what was p99 at this load" about any load in particular.

`accepted` and `stored` cover the whole run, warmup included. The latency columns cover the measured phase
only. `k6` is what the generator saw end to end; `svc` is the service's own histogram of the same requests
(ADR 0024) — two independent instruments on one quantity, which is what makes either believable.

| events/s | accepted | stored | non-202 | k6 p95 | k6 p99 | svc p95 | svc p99 | drain | sessions |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 250 | 30,753 | 30,753 | 0 | 0.9 ms | 2.1 ms | 1.0 ms | 1.3 ms | 0 s | 2,500 |
| 500 | 61,503 | 61,503 | 0 | 0.7 ms | 1.5 ms | 1.0 ms | 1.0 ms | 0 s | 5,000 |
| 1,000 | 123,003 | 123,003 | 0 | 1.0 ms | 3.6 ms | 1.0 ms | 2.0 ms | 0 s | 10,000 |
| 2,000 | 246,007 | 246,007 | 0 | 0.9 ms | 3.9 ms | 1.0 ms | 2.1 ms | 0 s | 20,000 |
| 4,000 | 492,005 | 492,005 | 0 | 1.0 ms | 4.2 ms | 1.0 ms | 1.7 ms | 1 s | 40,000 |
| 8,000 | 983,780 | 983,780 | 0 | 4.7 ms | 23.6 ms | 2.7 ms | 10.4 ms | 1 s | 80,000 |
| 8,000 (repeat) | 982,719 | 982,719 | 0 | 5.3 ms | 27.7 ms | 2.8 ms | 10.1 ms | 1 s | 80,000 |

Over the whole series: `events_rejected_total` has no series at all — nothing was ever rejected, for any of
its seven reasons — and `events_dropped_total{reason="bot"}` stayed at zero, so no event was accepted and then
discarded as a crawler.

**What the columns say.**

- **Nothing was lost at any rate.** `accepted` and `stored` are equal on every row. Ingest answers `202` as
  soon as the event is on its way to Kafka, so this is the only check that covers the whole path.
- **The processor kept up in real time.** `drain` is how long after the generator stopped the last row
  arrived: zero or one second throughout. No backlog ever built.
- **Latency is flat to 4,000 events/s** and rises modestly at 8,000, where the service's own p99 is 10 ms.
- **`sessions` is exactly ten times the rate**, by construction: the visitor pool scales with the rate so that
  every run has visitors behaving identically — six events a minute each — and sessionization state is cleared
  between runs. A fixed pool would have made a faster run also a run of busier visitors, and the series would
  be comparing two things at once.
- **At 8,000/s the generator was the limit, not the service.** It dropped 223 iterations in one run and 1,285
  in the other — 0.02% and 0.13% — which means it could not quite offer the rate it was asked for, so treat
  those two latency rows as indicative rather than exact. Under load the three services and their
  infrastructure used about 2.5 of the machine's 10 cores (ingest 1.2–1.8, PostgreSQL 0.4–1.0, processor 0.5,
  Redis 0.35, Kafka spiky), and k6 took another 1.5. **The ceiling of this system was not found.** Finding it
  needs the generator on a second machine, which this setup deliberately does not have.

## Reading the topic a second time (ADR 0006)

Delivery is at-least-once, and what makes that safe is that the write is idempotent: events are inserted with
`ON CONFLICT DO NOTHING RETURNING`, and sessions and rollups are derived only from the rows that insert
actually created (D-018, D-032). The experiment is one move — rewind the consumer group to the beginning of
`raw-events`, let it read everything again, and compare. `infra/k6/replay-check.sh` does exactly that.

This cannot be tested from the HTTP side: the event id is minted by ingest, so posting the same payload twice
is two legitimate events. The duplicate the design defends against is a redelivery from Kafka, and a rewind is
what reproduces it.

```
before   events=4279247 sessions=365615 page_hourly=4060204 custom_hourly=219043
after    events=4279247 sessions=365615 page_hourly=4060204 custom_hourly=219043
replayed 4279247 records in 229s
```

**4,279,247 records, read a second time, changed nothing.** All four numbers are identical. The replay ran at
roughly 18,700 records a second, faster than the live path because nothing had to be inserted.

Two smaller things fell out of the same numbers. The consumer group's lag after the rewind was 4,279,247,
exactly the number of stored events — every record on the topic has a row. And `page_hourly` plus
`custom_event_hourly` sum to 4,279,247 as well, so the rollups agree with the events they were built from.

## What the write-path test found

**The realtime visitor count is inflated by a replay.** The key was cleared to zero before the rewind; after
the topic had been read again it held 80,000 members — every visitor of the last run, counted as being on the
site right now, from history alone.

The cause is in `RawEventListener`: the recorder is handed every event in the batch rather than the ones the
insert created.

```java
int stored = persister.persist(sessionized);   // filters to the newly inserted rows internally
realtimeVisitors.record(sessionized);          // but this gets all of them
```

So D-032's discipline — increments come only from events that were really inserted — is not applied to this
one number, and the score written is the processing time rather than the event's own timestamp. That has a
second consequence beyond replays: while the consumer is behind, visitors are counted as present *now* even
though they were seen minutes ago.

It is the most visible number on the dashboard and on every public shared dashboard, and it was wrong for as
long as the ten minute retention kept the stale entries. It healed by itself, and a rewind is rare — but
"rare" includes the recovery procedure in `docs/deployment.md`, which starts a consumer from the beginning.

**Fixed in the same round.** The score is now the event's own timestamp, so a replay writes entries where they
belong in the past and the trim in the same pipeline takes them straight back out; `ZADD` carries `GT` so that
a late event can only ever move a score forward, which matters because without it the first half of the fix
would let an old event drag a visitor who really is present into the past and have the trim remove them. The
recorder is also handed what the batch stored rather than what arrived, which is the rule everything else
derived from a batch already followed (D-032) — on a replay of this size that alone is four million Redis
commands not issued.

Two regression tests came with it, and the one that matters was checked the only way a regression test can be:
by putting the old behaviour back and watching it fail. Worth noting that the code had been disagreeing with
its own documentation — both `RealtimeVisitorKeys` and the data flow notes already said the score was the
second the visitor was last seen.

## What was measured, part two: the read path

The queries behind the dashboard, against a corpus with two years of history. One question drives it, and it
is one the architecture already wrote down and never answered: [ADR 0007](adr/0007-events-sessions-and-additive-rollups.md)
counts unique visitors exactly, with `COUNT(DISTINCT)` over sessions, because visitors are not additive and no
rollup can hold them — and it says HyperLogLog sketches are the next step, "adopted on evidence rather than up
front". This is that evidence. Two more written claims were measured along the way, and both turned out to be
the wrong way round.

### The corpus

Produced by `infra/k6/seed-analytics.sql`, because the load generator cannot produce it: ingest stamps the
event time from the server clock (D-027), so k6 can only ever write "now", and a question about the last month
asked of twenty minutes of traffic reads almost nothing.

| | |
|---|---|
| events | 49,341,165 |
| sessions | 16,666,656 |
| hourly page rollup | 11,139,394 rows |
| span | 24 months, ending today |
| sites | one main site holding 90% of it, two neighbours |
| on disk | 15 GB of events, 5 GB of sessions, 21 GB of database |

Two details of the shape matter more than the size. **Visitor hashes are unique per site per day**, because
production rotates the salt daily (ADR 0005) and the same person is a different hash tomorrow — so "unique
visitors over 30 days" is close to the sum of thirty daily figures, and the distinct count is correspondingly
large. Modelling a visitor as stable across the period would have made every `COUNT(DISTINCT)` far cheaper
than the real one. And **the dimensions are skewed, not uniform**: the busiest page takes 12.6% of pageviews,
which is what a breakdown ordering by visitors actually faces.

The corpus is checked rather than assumed. Events are generated from sessions and the rollups aggregated from
the events, and six invariants are verified afterwards: no event without a session, `pageviews` and `events`
agreeing with the rows that exist, both rollups equal to a count over the events, and no visitor hash spanning
more than one day. The semi-join filters reach events through `session_id`, so incoherent ids would have
measured an empty result.

### The method

Each statement runs five times and the **median** is the number, with the spread shown next to it. A single
`EXPLAIN ANALYZE` is not a measurement here: back to back, the same statement came out at 2216, 3521 and
2348 ms — the run-to-run spread was wider than the differences being looked for. With medians the spread is
2–5% on most statements.

**Postgres is left exactly as production runs it**, which is the official image with no settings of its own:
`shared_buffers` 128 MB, `work_mem` 4 MB. That is a deliberate choice and not an omission. A number from a
tuned database would describe a deployment nobody has, and as it turns out the untuned setting is a finding in
its own right rather than an excuse.

### Results

Milliseconds of database work, main site, median of five runs. "One render" is what a single dashboard page
asks for: a summary, a timeseries, the goals and eight breakdowns.

| | today | 7 days | 30 days | 12 months |
|---|---:|---:|---:|---:|
| breakdown: page, from events (D-098) | 124 | 603 | **1,975** | **18,549** |
| goals, two goals in one pass (D-125) | 94 | 526 | **1,384** | **13,876** |
| timeseries, daily buckets (ADR 0020) | 39 | 218 | 551 | 4,830 |
| breakdown: source, from sessions | 41 | 232 | 342 | 3,728 |
| summary, no filter | 30 | 122 | 332 | 2,996 |
| summary, one filter (D-103) | 35 | 157 | 296 | 4,916 |
| semi-join: visits by page (D-104) | 22 | 105 | 254 | 4,498 |
| semi-join: events by entry page (D-104) | 29 | 111 | 235 | 6,376 |
| **one render, summed** | **575** | **3,093** | **6,637** | **66,345** |

The last row sums the queries a render issues, with the seven session-backed breakdown cards priced at the
source card's cost. Measured end to end through api instead — the same page, twelve real HTTP requests, median
of three passes — a render is **961 ms / 5,421 ms / 17,402 ms / 170,721 ms** for the same four periods.

### What the measurement found

**Today is fine and the default is not.** A day costs 575 ms of database work and nobody will notice it. The
period the dashboard opens on is 30 days, and that costs **6.6 seconds** — issued as twelve concurrent
requests, so the wall clock is better than the sum, but the database is asked for 6.6 seconds of work either
way and it does not have twelve spare cores. A one-year custom range costs **66 seconds**, and `scope.md` puts
custom ranges in v1.

**Half of every total is two queries.** The page breakdown and the goals both group over events and both do a
`COUNT(DISTINCT)`; between them they are 3.4 of the 6.6 seconds at 30 days. The remaining seven breakdown
cards read sessions at 342 ms each, which is the other half. No single card is the problem; the page is.

**ADR 0007's question is still open, and the reason has changed.** The cost is not the distinct count in
itself — it is the number of rows reached to compute it. The plan seq-scans the partitions rather than using
`(site_id, started_at)`, correctly: the main site is 90% of the table, so the index offers no selectivity, and
30 days means 1.4 million session rows scanned to produce 979,278 for the distinct count. Raising `work_mem`
from production's 4 MB to 64 MB stops the sort spilling to disk (28 MB of temp files at 30 days, 139 MB for
the goals) and buys **15–25%**, then plateaus. So sketches would only pay if they were pre-aggregated into the
hourly rollup, turning a million-row scan into around 720 rows — which is a different and much larger change
than "adopt HyperLogLog", and the measurement is what makes that visible.

**D-103 is inverted at this cardinality.** The design says filtering costs more, because the rollup holds no
dimension beyond the page and a filtered pageview count has to come from the events instead. Measured, the
filtered summary is *faster* at 30 days — 296 ms against 332 ms. The hourly rollup is one row per
`(site, hour, path)`, and with 500 paths that is 344,000 rows for 30 days, so reading it is not cheaper than
counting events the filter has already narrowed. The rollup earns its place only where path cardinality is
low. At 12 months the order flips back the expected way, and by then both numbers are unusable.

**D-104's semi-joins are the cheapest shapes measured, not the dearest.** The source comments called them the
most expensive thing in the system. At 30 days they are 235 and 254 ms, below every other query, because a
filter naming one page or one entry page is highly selective — the work the semi-join avoids outweighs the
work it adds.

### The gap between the two halves, and what it turned out to be

The database said 6.6 seconds for a 30-day render and the HTTP measurement said 17.4. Chasing that down found
something worth more than the number.

**The api's own overhead is about ten milliseconds.** Logging every statement for one request shows the whole
of what it does: `BEGIN READ ONLY` at 0.025 ms, the site lookup that decides whether the dashboard is shared
at 0.078 ms, `COMMIT` at 0.009 ms, and the report query. Subtract the query from the HTTP total and roughly
ten milliseconds remain for Spring, JDBC, Jackson and the round trip. There is no extra layer to pay for.

**The summary query really is twice as slow in production as any hand measurement said, and the cause is the
plan cache.** The pgjdbc driver switches to a server-side named prepared statement after five executions —
the log shows `execute S_3` rather than `execute <unnamed>` — and PostgreSQL then builds a generic plan and
keeps it. For this statement the generic plan is about twice the cost. Measured by changing one setting and
nothing else:

| `plan_cache_mode` | summary, 30 days, end to end |
|---|---|
| `force_custom_plan` | 390 ms |
| `auto` (the default, and production) | 750 ms |

That first comparison said the penalty was specific to the summary statement, because the page breakdown, the
goals and the timeseries measured the same under both settings. It was wrong, and the reason is the same
threshold: that comparison made **four** requests per endpoint, so the arm that was supposed to show generic
plans never reached the fifth execution and never built one. Measured properly, with the harness running five
full iterations, the penalty is spread across the whole page.

**That makes the method above wrong in a way worth naming.** Timing a statement through psql's `\bind`, which
is what the timings harness does, always uses an unnamed statement and therefore always gets a custom plan.
Every hand-measured number in this document is the better of two plans, and production stops using it after
the fifth request. The remedy is one line, and it has been applied with its own before and after — below.

**What is left of the gap is the run's own shape.** The summed row prices seven breakdown cards at the source
card's cost; measured individually they come to about 2.9 seconds rather than 2.4, since region and city group
over more values. The rest is contention: the end-to-end run walks all four periods in one loop, so a
twelve-month sweep is still settling when the thirty-day requests arrive. Cache was ruled out — re-running the
SQL timings right after that sweep gave 6,611 ms against 6,637 ms before it — but an idle cache is not an idle
system, and that distinction is the one this measurement got wrong first.

### The fix, and what it bought

`spring.datasource.hikari.connection-init-sql: SET plan_cache_mode = 'force_custom_plan'`, on api only.

`force_custom_plan` rather than `prepareThreshold=0`: that would stop the statement being prepared at all and
pay to parse it again every time, while this keeps the parse and drops only the part that hurts. Set on the
connection pool rather than on the database role, so it travels with the service instead of being a step
somebody has to remember on a new database. Applied to api alone — the processor's writes were not measured,
and changing them on the strength of somebody else's result is the mistake this whole section is about.

Thirty days, five iterations of the full page, medians, the same harness on both sides:

| | before | after | |
|---|---:|---:|---:|
| one render | 10,429 ms | **7,939 ms** | −24% |
| summary | 865 ms | **375 ms** | −57% |
| breakdown: page | 3,608 ms | **2,010 ms** | −44% |
| goals | 2,315 ms | 1,669 ms | −28% |
| timeseries | 943 ms | 626 ms | −34% |
| breakdown: city | 478 ms | 520 ms | +9% |

A second run of the after case landed at 8,114 ms against 7,939, so the difference is comfortably larger than
the spread between runs.

It is guarded by a test. The absence of that line is completely silent — nothing fails, nothing is logged, the
numbers are simply worse, and the first four executions of any statement look identical either way. The test
asserts `show plan_cache_mode` on a pooled connection, and was checked the only way a regression test can be:
by taking the line out and watching it fail with `but was: "auto"`.

It does not make the dashboard fast. A thirty-day render still asks for about eight seconds of database work,
and that was the open question — answered two sections below, where the daily visitor rollup takes it to 4.3 s
and a twelve-month render from seventy seconds to forty.

### An optimisation that measured twice as fast and changed nothing

The breakdown cards looked like the obvious thing to fix. Seven of the eight group the same visits by a
different column, so asking one at a time has the database read the same million rows seven times, and
`GROUPING SETS` reads them once. A spike against this corpus said exactly that: seven separate statements
cost 5,210 ms, one grouping-sets statement 2,575 ms, and the plan confirmed the mechanism by scanning the
partitions once rather than seven times.

Built and measured at the level that matters, it bought nothing.

| | runs (ms) | median |
|---|---|---:|
| eight requests | 7,371 · 7,260 · 7,721 | 7,371 |
| one request | 7,534 · 7,348 · 7,905 · 7,803 | 7,669 |

Two reasons, and neither is visible from the spike.

**The rows are already hot by the time the cards are drawn.** A render asks for the summary, the timeseries
and the goals first, and all three read the same sessions. In the spike the seven statements were the only
thing running and each paid for its own reads; in a render they do not, so being separate costs much less
than it appeared to.

**The combined query has to give up the top-N sort.** Eight separate statements each end in
`order by visitors desc limit 10`, which PostgreSQL answers with a top-N heapsort that never materialises
more than ten rows. Ranking within grouping sets needs `row_number()` over every group instead. That showed
up first as a 31% regression on a *single* card, which a fast path for the one-dimension case fixed — but
the combined form cannot avoid it, and what the single scan saves the ranking gives back.

So the change was reverted: a wider contract, a nested response shape, two SQL paths and a translation layer
in the web client, in exchange for nothing measurable. What is kept is the finding, and the habit that
produced it — the sequencing was deliberately "put it behind the endpoint and measure a render", precisely
because a query measured on its own is not the same query in its place.

### Unique visitors turn out to be additive, and that changes what is worth building

The remaining cost is dominated by counting distinct visitors over a range, which ADR 0007 says no rollup can
hold. Measured, that is true of hours and false of days, because ADR 0005 rotates the hashing salt every UTC
day: the same person is a different hash tomorrow, so two days hold disjoint sets and a range's distinct count
is the sum of its days'. Exactly — not approximately.

| thirty days, one site | unique visitors |
|---|---:|
| counted live over the range | **54,176** |
| summed from per-UTC-day counts | **54,176** |
| summed from per-*local*-day counts | 62,864 (+16%) |

Per country, across 193 of them, the difference between the live count and the summed one was zero. So the
rollup can hold a breakdown too, and it holds it exactly rather than as a sketch.

Two conditions, both sharp. The grain has to be the UTC day, since that is the salt's grain — the local-day
figure above shows what happens otherwise. And a site's own day does not align with it (ADR 0020), so the
range a dashboard asks for begins and ends partway through a UTC day:

| thirty days, Istanbul window | unique visitors |
|---|---:|
| counted live (the right answer) | **53,265** |
| summed from whole overlapping UTC days | 54,176 (1.7% high, and silent) |
| interior days from the rollup, two edge days counted live | **53,265** |

What it would buy, on this corpus: the summary's visitor count goes from 17.7 ms to 0.1, a country breakdown
from 58.5 ms to 1.0, and neither grows with the number of events — a rollup is sized by days times distinct
values, so the same figures would hold against fifty million rows where the live queries cost around 340 ms.

What it would not cover is worth saying first: anything filtered, since a filter on one dimension cannot be
answered from another's rollup without pre-aggregating the combinations; the goals, which are user-defined and
report history from the day they are created (D-122); and the page breakdown, which is grouped from events
rather than sessions and would need a rollup of its own. That leaves roughly the session-backed half of a
render — an estimate, and after the grouping-sets result above, one nobody should believe until it is measured
as a render.

### Building it, and what the render actually did

The estimate above was roughly right about the half it could see and silent about the rest, which is the
reason it was not trusted. What was built is a table of one row per site, per UTC day, per dimension value,
holding that day's distinct visitors alongside its visits, pageviews, bounces and duration; a watermark row
saying how far it is known to be complete; and a reader that takes the whole interior UTC days of a range
from it and counts the two partial days at the edges live.

The measurement is a before and an after of the same binary against the same corpus, a minute apart. Nothing
was rebuilt between them and no setting was touched: the only difference is whether the table had been filled
and the watermark moved. The filtered arm is the control, because a filtered report never reads the rollup —
if it moved, something other than the rollup moved with it.

Milliseconds, median of five, one request at a time, 49.4M events over 24 months:

| endpoint | 30d before | 30d after | | 12m before | 12m after | |
|---|---:|---:|---:|---:|---:|---:|
| summary | 390 | **108** | −72% | 3,033 | **790** | −74% |
| breakdown, source | 377 | **21** | −94% | 3,586 | **421** | −88% |
| breakdown, country | 338 | **14** | −96% | 3,468 | **116** | −97% |
| breakdown, region | 482 | **13** | −97% | 5,195 | **95** | −98% |
| breakdown, city | 476 | **14** | −97% | 5,350 | **82** | −98% |
| breakdown, browser | 350 | **7** | −98% | 3,787 | **19** | −99.5% |
| breakdown, os | 349 | **8** | −98% | 3,578 | **13** | −99.6% |
| breakdown, device | 361 | **5** | −99% | 3,599 | **8** | −99.8% |
| breakdown, page | 1,836 | 1,882 | +3% | 19,053 | 19,502 | +2% |
| timeseries | 572 | 595 | +4% | 5,168 | 5,470 | +6% |
| goals | 1,650 | 1,641 | −1% | 12,526 | 14,434 | +15% |
| **whole dashboard** | **7,227** | **4,349** | **−40%** | **69,969** | **40,144** | **−43%** |
| whole dashboard, filtered *(control)* | 2,205 | 2,231 | +1% | 28,054 | 29,106 | +4% |

Seven breakdowns and the summary; a twelve-month dashboard from seventy seconds to forty. The endpoints the
rollup answers stopped growing with the range: a twelve-month browser breakdown is now 19 ms against a
thirty-day one at 7, where before it was 3,787 against 350. That is the point of the table rather than a
side effect of it — the figures are sized by days times distinct values, not by events.

The control arm is the part worth reading twice. It moved by +1% and +4%, which is small but is not zero, and
every endpoint the rollup does not serve moved the same way: the page breakdown +2%, the timeseries +6%, the
goals +15%. The rollup is 253 MB and the database is 21 GB against a 128 MB `shared_buffers`, so a new table
that every report touches is new competition for cache, and the queries that gained nothing from it paid for
it. The honest summary is that this bought 30 s on a twelve-month render and cost something under 2 s
elsewhere, not that it was free.

What it cost to keep: 1.24M rows and 253 MB against 16.7M sessions and 5 GB, so about a twentieth. Filling it
for 712 days took 93 s of work, 131 ms per day, though the job paces itself at thirty days per ten-minute
run and a two-year backfill therefore takes about four hours of wall clock. Nothing waits for it: a day the
rollup has not reached is counted live, which is what the thirty-day column's *before* figures already are.

### A bug this found, which no test and no measurement would have

The reader's first version sent every session-backed dimension to the rollup. The writer computes seven of
them. The other seven — entry page, exit page and the five UTM dimensions — are left out deliberately,
because a row per campaign or per entry page per day would make the table a second copy of the sessions
rather than a summary of them.

Asking the table for a dimension it does not hold does not fail. It returns no rows, and the reader adds no
rows to the two partial days at the edges of the range, so a twelve-month entry-page breakdown would have
come back holding two days of data and looking entirely ordinary. The dashboard does not draw those cards
yet, which is the only reason it was not shipped.

What it changed: the set is now a property of each dimension rather than something inferred from which table
it lives on, and both services pin the same seven names in a test, since the two halves are in different
services and nothing else makes them move together. Inferring it was the bug — every one of the fourteen is
session-backed, so the property that mattered was not visible in the thing being asked.

The same build also found a day could be counted too early. A day is counted once and never recounted, so a
session that started at 23:58 and was still being extended after midnight, or a batch delayed by consumer
lag, would be missing from a figure that is never corrected. A day is now left for an hour after it ends
before it is counted, which costs an hour of answering yesterday from the sessions and nothing else. The
hourly rollups never needed this, because they add to a running total and a late batch simply adds to it; a
distinct count is not a total you can add to, which is the whole reason this table exists.

### What is slow now

The twelve-month dashboard is forty seconds, and three endpoints are thirty-nine of them: the page breakdown
at 19.5 s, the goals at 14.4 s and the timeseries at 5.5 s. All three are grouped from events rather than
sessions, which is 49M rows against 16.7M, and none of them is a distinct count that a daily grain would
fix in the same way — the timeseries is cut in the site's own timezone, and the goals are user-defined. That
is the next thing to measure, and the figure to beat is now the one in the table above rather than the one
this section started with.

### Two mistakes in the measuring, both of which read as good news

Worth writing down because both produced a plausible number rather than an error.

**`select count(*) from (<query>)` lets the planner skip the query.** It is the obvious way to run a statement
and discard its rows. But nothing outside the subquery references its aggregates, and an aggregate with no
`GROUP BY` returns one row whatever the data, so the planner stops computing them: four of the eight
statements reported **0 ms**, including two measured at 332 and 1,384 ms moments earlier. Only the ones with a
`GROUP BY` were honest, because there the row count depends on the data. The harness now casts each row to
text, which forces every output column.

**A lateral subquery that references nothing is evaluated once.** The corpus seeder drew its random values in
a `LATERAL` that depended on no outer column, so it had no lateral dependency and was evaluated a single time
per statement — every one of the thirty thousand visits in a chunk came out identical, same day, same page,
same country. Nothing complained. The shape check is what caught it: two distinct paths in the whole corpus.
Before that, the same seeder matched zero rows for a while because the pool lookups were written as
`where i = floor(random() * n)`, and `random()` is volatile, so each pool row was compared against its own
fresh number.

## Reproducing this

### Reproducing the write path

From the repository root, with Docker running and `infra/geoip/GeoLite2-City.mmdb` present:

```bash
# 1. Build the service images for this architecture (the published ones are amd64).
for s in ingest processor api; do
  docker build -f infra/docker/service.Dockerfile --build-arg SERVICE="$s" -t "wholestory-local-${s}:latest" .
done

# 2. The production file expects Traefik's network and a GeoIP volume.
docker network create dokploy-network
docker volume create wholestory-loadtest_geoip
docker run --rm -v wholestory-loadtest_geoip:/geoip -v "$PWD/infra/geoip:/src:ro" alpine:3 \
  cp /src/GeoLite2-City.mmdb /geoip/

# 3. Bring up the stack. web, Grafana and the GeoIP updater are not needed for a write-path run.
docker compose -p wholestory-loadtest --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.loadtest.yml \
  up -d postgres kafka redis ingest processor api prometheus

# 4. Register the site the generator posts as: ingest only accepts domains Site Management announced
#    on site-events (ADR 0019), so without this every event is rejected with 400 unknown_domain.
curl -fsS -c /tmp/ws-cookie -H 'Content-Type: application/json' -X POST \
  http://localhost:18080/api/auth/register \
  -d '{"email":"loadtest@example.test","name":"Load Test","password":"loadtest-password"}'
#    then POST /api/sites with the organizationId from that response and domain loadtest.example

# 5. Measure, then check idempotency.
infra/k6/run-series.sh 250 500 1000 2000 4000 8000
infra/k6/replay-check.sh

# 6. Tear it down. The volumes belong to this project only; local development is untouched.
docker compose -p wholestory-loadtest --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.loadtest.yml down -v
docker network rm dokploy-network
```

The generator refuses to start a run that would measure the wrong thing. Admission control is per visitor, 60
events a minute (ADR 0016), so a pool too small for the rate would spend the run collecting `429`s; the script
works out the arithmetic in `setup()` and fails with the pool size it would need. It also probes the endpoint
once before starting, so an unregistered domain stops the run instead of producing a summary of `400`s.

### Reproducing the read path

```bash
# 1. Bring up the stack. processor is started for its migrations: it owns the analytics schema and the
#    api_* views api reads (D-012), and applying those by hand is what drifted a database once (D-145).
docker network create dokploy-network
docker compose -p wholestory-readpath --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.readpath.yml \
  up -d postgres kafka redis processor api prometheus

# 2. Seed the corpus. Around eight minutes for fifty million events, plus the vacuum.
#    Check the shape it prints at the end before trusting anything measured on it.
infra/k6/seed-analytics.sh 24 45000000 2500000 2

# 3. Measure. Only Postgres is needed for this, and on a machine this size the idle JVMs are worth
#    stopping first — one sweep was killed by the host running out of memory with them running.
docker compose -p wholestory-readpath --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.readpath.yml stop api processor kafka prometheus
infra/k6/read-path-timings.sh today
infra/k6/read-path-timings.sh 7d
infra/k6/read-path-timings.sh 30d
REPS=3 infra/k6/read-path-timings.sh 12m

# 4. The render-level before and after of the daily visitor rollup. Order matters: the stack is left as it
#    was built, so the two arms differ only in whether the rollup is filled. processor is stopped because
#    its scheduled job would otherwise begin filling the table during the *before* arm.
docker compose -p wholestory-readpath --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.readpath.yml stop processor
docker compose -p wholestory-readpath --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.readpath.yml \
  run --rm -e PERIODS=30d,12m -e ITERATIONS=5 k6          # before: the table is empty
cp infra/k6/results/read-path.json infra/k6/results/before.json
infra/k6/backfill-visitor-daily.sh                        # 712 days in about 95 seconds
docker compose -p wholestory-readpath --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.readpath.yml \
  run --rm -e PERIODS=30d,12m -e ITERATIONS=5 k6          # after

# 5. Ask why, once a number is worth explaining. This one prints plans instead of timings.
infra/k6/read-path-queries.sh 30d

# 6. The same with one setting changed, which is the only honest way to attribute a difference to it.
WORK_MEM=64MB infra/k6/read-path-timings.sh 30d

# 7. Tear it down.
docker compose -p wholestory-readpath --env-file infra/k6/loadtest.env \
  -f infra/docker-compose.prod.yml -f infra/k6/docker-compose.readpath.yml down -v
docker network rm dokploy-network
```

The seeder truncates `analytics` before it starts, because its primary keys come from a counter that restarts
at zero and a second run would collide rather than add. It is the one script in this repository allowed to
write into that schema directly, and only because it is a measurement rig on a local machine: in production
schema ownership is the boundary (D-012, D-037).

## Not measured yet

- **What the page breakdown, the goals and the timeseries cost, and whether a rollup can help them.** They
  are 39 of the 40 seconds a twelve-month render now takes. All three are grouped from events rather than
  sessions, and none is a distinct count that the daily grain would fix the same way.
- **Where the read path's remaining HTTP overhead comes from.** The gap between summed database work and the
  end-to-end figure was mostly the generic plan, which is fixed above; what is left has not been accounted
  for, and the summed figure it is compared against prices seven of the breakdown cards by proxy.
- **The production server's capacity.** The VPS has far more memory and a different architecture.
- **More than one ingest replica.** Horizontal scaling is the premise of ADR 0002 and is untested.
- **The public path's capacity.** It was exercised in production at a trivial rate — 31 events through
  Traefik, all stored, all reported — but never loaded, and loading somebody's live service to
  find its ceiling is not a measurement worth the cost.
