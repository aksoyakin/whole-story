# Architecture Decision Records

| # | Decision | Status |
|---|---|---|
| [0000](0000-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0001](0001-selective-domain-driven-design.md) | Selective Domain-Driven Design | Accepted |
| [0002](0002-services-split-by-scaling-profile.md) | Three services, split by scaling profile; a modular monolith inside | Accepted |
| [0003](0003-schema-ownership-and-database-roles.md) | One PostgreSQL, a schema per context, enforced by database roles | Accepted |
| [0004](0004-kafka-for-event-ingestion.md) | Kafka between ingest and processing | Accepted |
| [0005](0005-privacy-by-design.md) | Privacy by design: the IP address never leaves ingest | Accepted, partly implemented |
| [0006](0006-idempotent-at-least-once-processing.md) | At-least-once delivery with idempotent writes | Accepted |
| [0007](0007-events-sessions-and-additive-rollups.md) | Events, sessions and additive-only rollups | Accepted |
| [0008](0008-sessionization-state-in-redis.md) | Session state in Redis | Accepted |
| [0009](0009-transactional-outbox-with-spring-modulith.md) | Transactional outbox with Spring Modulith | Accepted |
| [0010](0010-pure-domain-layer.md) | A pure domain layer and manual mappers | Accepted |
| [0011](0011-backend-for-frontend.md) | The web app as a backend-for-frontend | Accepted |
| [0012](0012-build-and-deployment.md) | Build and deployment | Accepted |
| [0013](0013-geoip-lookups-and-database-distribution.md) | GeoIP lookups and database distribution | Accepted |
| [0014](0014-user-agent-parsing-and-bot-filtering.md) | User-Agent parsing and bot filtering | Accepted |
| [0015](0015-referrer-source-classification.md) | Referrer source classification | Accepted |
| [0016](0016-ingest-admission-control.md) | Admission control at ingest | Accepted |
| [0017](0017-bounce-definition.md) | What counts as a bounce, and where that is defined | Accepted |
| [0018](0018-authentication-and-sessions.md) | Authentication and sessions | Accepted |
| [0019](0019-telling-ingest-which-domains-are-tracked.md) | Telling ingest which domains are tracked | Accepted |
| [0020](0020-reporting-queries.md) | Reporting queries, and the shape of the dashboard they feed | Accepted |
| [0021](0021-password-reset-and-sending-mail.md) | Password reset, and how this product sends mail | Accepted |
| [0022](0022-goals-and-conversion-rate.md) | Goals and the conversion rate | Accepted |
| [0023](0023-deleting-the-data-of-a-removed-site.md) | Deleting the data of a removed site | Accepted |
