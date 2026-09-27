# 0002. Three services, split by scaling profile; a modular monolith inside

- Status: Accepted
- Date: 2026-09-26

## Context
Splitting services per bounded context would create four or more services with the same team, release cadence
and traffic shape for most of them, plus the cost of distributed calls between them. Some parts, however, do
scale and fail differently: receiving events is I/O-bound and spiky, processing them is CPU-bound, and the
dashboard API serves a handful of users.

## Decision
Three backend deployables:

| Service | Profile |
|---|---|
| `ingest` | Accepts tracker events, applies privacy processing, publishes to Kafka. Stateless, I/O-bound, scaled with traffic. |
| `processor` | Consumes events, sessionizes, writes to PostgreSQL. CPU-bound, scaled with Kafka partitions. |
| `api` | Identity & Access, Site Management and Analytics queries. |

`api` is a modular monolith: each bounded context is a Spring Modulith module, and module boundaries are
verified by a test (`ModularityTest`) on every build.

## Consequences
- Deploying the API does not pause event processing; a traffic spike does not slow down dashboards.
- Contexts inside `api` can still be extracted later because their boundaries are enforced, not implied.
- Three deployables are more to operate than one; this is accepted for the isolation above.
