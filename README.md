# Whole Story

Privacy-first, cookieless web analytics. No cookies, no personal data, no IP addresses stored.

> Work in progress.

## Architecture

| Component | Responsibility |
|---|---|
| `services/ingest` | Receives events from the tracking script, hashes visitors, resolves GeoIP, drops the IP, publishes to Kafka |
| `services/processor` | Consumes events, parses User-Agents, builds sessions, writes idempotently to PostgreSQL, maintains hourly rollups |
| `services/api` | Modular monolith: Identity & Access, Site Management, Analytics (read side) |
| `libs/event-contracts` | Versioned Kafka message contracts |
| `web` | Next.js: landing page, dashboard, public shared dashboards |
| `tracker` | Tracking script (< 1 KB) |
| `infra` | Docker Compose, Prometheus, Grafana, k6 load tests |
| `docs` | Architecture Decision Records and C4 diagrams |

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
the generator, not the service, was the limit. The method, the deviations from production, the full table and
the defect the test found are in [docs/performance.md](docs/performance.md).

## Licence

Whole Story is licensed under the [GNU General Public License v3](LICENSE). The referrer database it bundles
(`referers.yml`, from [referer-parser](https://github.com/snowplow-referer-parser/referer-parser), based on
Matomo's `SearchEngines.php` and `Socials.php`, copyright 2012 Matthieu Aubry) is published under that licence.

## Attribution

This product includes GeoLite2 data created by MaxMind, available from [maxmind.com](https://www.maxmind.com).
