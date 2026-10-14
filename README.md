# Whole Story

Privacy-first, cookieless web analytics. No cookies, no personal data, no IP addresses stored.

> Work in progress.

## Architecture

Context, container and component diagrams: [docs/architecture](docs/architecture/README.md).

| Component | Responsibility |
|---|---|
| `services/ingest` | Receives events from the tracking script, hashes visitors, resolves GeoIP, drops the IP, publishes to Kafka |
| `services/processor` | Consumes events, parses User-Agents, builds sessions, writes idempotently to PostgreSQL, maintains hourly rollups |
| `services/api` | Modular monolith: Identity & Access, Site Management, Analytics (read side) |
| `libs/event-contracts` | Versioned Kafka message contracts |
| `web` | Next.js: landing page, dashboard, public shared dashboards |
| `tracker` | Tracking script (< 1 KB) |
| `infra` | Docker Compose, Prometheus, Grafana, k6 load tests |
| `docs` | Architecture Decision Records, C4 diagrams, deployment and performance |

## Tech stack

Java 27 · Spring Boot 4.1 · Spring Modulith · PostgreSQL · Kafka (KRaft) · Redis · Next.js · Prometheus · Grafana · Testcontainers · k6

## Build

```bash
docker compose -f infra/docker-compose.yml up -d   # PostgreSQL, Kafka, Redis for local development
./mvnw verify                                      # backend: unit + Testcontainers integration tests
corepack pnpm install && corepack pnpm check       # frontend workspace (tracker, web)
```

Deployment: see [docs/deployment.md](docs/deployment.md). Design decisions: see [docs/adr](docs/adr/README.md).

## Measured

On one Apple M4 with 10 cores, running the production images and the production Compose file, with the load
generator on the same machine:

- **8,000 events a second accepted**, with the service's own p99 for `POST /api/event` at 10 ms; latency is
  flat up to 4,000 events a second (p99 under 5 ms).
- **Nothing was lost.** Every event answered `202` has a row: 983,780 accepted, 983,780 stored. No event was
  rejected and none was discarded as a crawler.
- **The processor kept up in real time** — the last row landed one second after the generator stopped.
- **4,279,247 records were read a second time and changed nothing**, which is what makes at-least-once
  delivery safe (ADR 0006).

That is a floor rather than a capacity: at the highest rate the services used about 2.5 of the ten cores and
the generator, not the service, was the limit.

Reading is the slower half, and it was measured against a seeded corpus of 49.4M events over 24 months. The
dashboard opens on seven days, and a whole render costs **575 ms for today, 3.1 s for seven days and 6.6 s
for thirty**. The figure the work below is quoted against is a **twelve-month** range: the three period
buttons do not reach it, but the date fields beside them do, up to a capped 366 days, and that is where the
costs that grow with the range show up at all.

Five changes, each measured at render level rather than on the query alone:

- planning reporting queries for the arguments they actually get, rather than letting a generic plan be
  reused (−24%);
- counting each finished day's unique visitors once, so a range can add days up instead of reaching for
  every session in it — unique visitors turn out to be additive across days, because the hashing salt
  rotates daily (twelve months 70 → 40 s);
- rolling the page breakdown up the same way, and dropping two numbers from the breakdown contract that
  nothing had ever rendered — one of them cost nine seconds on its own (twelve months 46.9 → 24.4 s, the
  page breakdown itself 18.9 s → 133 ms);
- rolling up the entry and exit pages too, which are the same path axis seen from the visit and therefore
  two more grouping sets in a statement that already reads the day, not another pass over it (twelve months
  26.2 → 17.2 s, the two cards 3.8 s and 4.0 s → 148 ms and 62 ms);
- matching each goal before counting it, instead of asking for a distinct count per goal over every row the
  scan touched — identical answers from a far smaller input, adopted after measuring that what it buys
  depends on how selective the goal is (twelve months, the goals endpoint 12.3 → 7.0 s, a whole render
  18.6 → 13.3 s). The time series stopped computing the measure the chart does not draw in the same round:
  the pageview chart went from about 4.8 s to 474 ms.

Twelve months is now thirteen seconds and twelve of those are two endpoints. What remains in each is the part
that cannot be pre-aggregated, for two different reasons: a goal matching a *set* of paths needs distinct
visitors across that set, which is not the sum of the per-path counts, and the visitors series buckets by the
site's local day while the hashing salt rotates on the UTC day, so one local day straddles two. Those are
properties of the design rather than work left undone, which is the more useful thing for a figure to be.

The method, the deviations from production, the full tables, the defect the load test found, an optimisation
that measured twice as fast and changed nothing, and two measurements that turned out to be measuring the
measuring are all in [docs/performance.md](docs/performance.md).

## Licence

Whole Story is licensed under the [GNU General Public License v3](LICENSE). The referrer database it bundles
(`referers.yml`, from [referer-parser](https://github.com/snowplow-referer-parser/referer-parser), based on
Matomo's `SearchEngines.php` and `Socials.php`, copyright 2012 Matthieu Aubry) is published under that licence.

## Attribution

This product includes GeoLite2 data created by MaxMind, available from [maxmind.com](https://www.maxmind.com).
