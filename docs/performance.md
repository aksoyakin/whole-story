# Performance

Measured numbers for the write path, and how to get them again. Nothing here is estimated: every figure below
came out of a run that can be repeated with two scripts in this repository.

## What was measured

The write path only — `tracker → ingest → Kafka → processor → PostgreSQL`. Throughput on its own would be a
weak claim, so each run answers four questions at once:

1. How many events a second does ingest accept, and how long does a request take?
2. Does the processor keep up, or does a backlog build?
3. Does everything that was accepted end up stored?
4. Does reading the topic a second time change any of the stored numbers (ADR 0006)?

Dashboard query latency is **not** measured here. It depends almost entirely on how much data the database
holds, so it needs a realistic corpus seeded first; until that exists, a number would describe an empty table.
That is a separate exercise.

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

## Results

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

## What the test found

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

## Reproducing this

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

## Not measured yet

- **Dashboard query latency**, which needs a seeded corpus first.
- **The production server's capacity.** The VPS has far more memory and a different architecture.
- **More than one ingest replica.** Horizontal scaling is the premise of ADR 0002 and is untested.
- **The public path's capacity.** It was exercised in production at a trivial rate — 31 events through
  Traefik, all stored, all reported — but never loaded, and loading somebody's live service to
  find its ceiling is not a measurement worth the cost.
