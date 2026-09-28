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

## Attribution

This product includes GeoLite2 data created by MaxMind, available from [maxmind.com](https://www.maxmind.com).
