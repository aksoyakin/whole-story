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
| [0009](0009-transactional-outbox-with-spring-modulith.md) | Transactional outbox with Spring Modulith | Accepted, not yet implemented |
| [0010](0010-pure-domain-layer.md) | A pure domain layer and manual mappers | Accepted |
| [0011](0011-backend-for-frontend.md) | The web app as a backend-for-frontend | Accepted |
| [0012](0012-build-and-deployment.md) | Build and deployment | Accepted |
| [0013](0013-geoip-lookups-and-database-distribution.md) | GeoIP lookups and database distribution | Accepted |
