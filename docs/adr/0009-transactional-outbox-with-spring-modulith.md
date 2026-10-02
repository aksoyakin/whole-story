# 0009. Transactional outbox with Spring Modulith

- Status: Accepted, implemented
- Date: 2026-09-26

## Context
When a site is registered, `ingest` must learn about its domain. Saving the site and publishing an event to Kafka
are two separate operations: if the database commits and the publish fails, the site never receives data.

## Decision
Domain events leaving the `api` service go through a transactional outbox, using Spring Modulith's event
publication registry (JDBC) and event externalization to Kafka rather than a hand-written outbox table.
Example flow: `SiteRegistered` → outbox → Kafka → `ingest` adds the domain to its allow-list in Redis.

## Implementation status
Implemented with Site Management. Three details were decided while building it:

- The **JDBC** registry rather than the JPA one, because only the JDBC variant lets the table be placed in a
  schema of its own (`spring.modulith.events.jdbc.schema`), and the outbox is neither bounded context's data.
- **Resending outstanding publications has to be switched on.** Modulith records a failed publication but does
  not retry it unless `republish-outstanding-events-on-restart` is set, so the guarantee below was not true until
  it was. It was found the way such things are: a site registered while api could not reach the broker, and the
  announcement simply never went out.
- **Startup is no longer the only retry.** That setting only resends while the application is coming up, so a
  broker or mail host that was briefly away left a publication waiting until the next deploy. A scheduled job
  (`OutboxResubmission`) now resubmits publications older than five minutes, which means consumers may see an
  event twice — they are idempotent either way
  ([ADR 0006](0006-idempotent-at-least-once-processing.md), [ADR 0021](0021-password-reset-and-sending-mail.md)).
- Modulith's own schema initialisation is **switched off** and the table is created by a Flyway migration like
  every other table here. Naming a schema otherwise makes Modulith run `CREATE SCHEMA` at startup, which the api
  role has no privilege for and does not need. The migration has to match the structure version Modulith expects;
  it says so by failing on a missing column, which is how the first attempt was caught.
- Externalization is configured **in code**, not with `@Externalized`. The annotation would have to sit on the
  domain event, and the domain layer carries no framework ([ADR 0010](0010-pure-domain-layer.md)). Configuring it
  also keeps the published message a versioned record in `event-contracts`, so the domain event never becomes a
  contract (D-041).

What goes on the wire, and how the consumer rebuilds its state from it, is
[ADR 0019](0019-telling-ingest-which-domains-are-tracked.md).

## Consequences
- An event is published if and only if the transaction that produced it commits; a publication that failed is
  retried when the service next starts.
- Consumers must tolerate duplicates.
- The registry's rows are visible in the database, so a publication that never completed can be found by looking
  rather than guessed at. An integration test asserts that a registration's row ends up completed.
