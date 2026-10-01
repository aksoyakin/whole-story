# 0019. Telling ingest which domains are tracked

- Status: Accepted
- Date: 2026-10-01

## Context
`ingest` accepts an event only for a registered domain, and it has to decide that on every request without a
database of its own ([ADR 0002](0002-services-split-by-scaling-profile.md)). Until now the allow-list was a Redis
hash filled in by hand. Site Management can fill it instead — the question is how, and what happens when Redis
is empty.

Redis is a cache in this system: it also holds sessionization state, the daily salt and rate-limit buckets, all of
which can be lost without consequence. The allow-list cannot. If it disappears, `ingest` answers
`400 unknown domain` to every real visitor, and that loss is invisible: the tracker fires and forgets, so nothing
retries and no error reaches anyone. A cache that must never be cold is the wrong design.

## Decision
- Site Management publishes to a **log compacted** Kafka topic, `site-events`, **keyed on the domain**. The topic
  is therefore not a stream of changes but the current state of every domain: the last record for a key is the
  whole truth about it, so a consumer with nothing cached can rebuild the entire list from the log.
- The message is **one self-describing record**, `TrackedDomainV1`, carrying `tracked: true|false` rather than a
  registered/removed pair of types. A consumer reads every record the same way, and the contracts stay free of
  the type discriminator two message types would need. A removal is an ordinary record, not a tombstone, so a
  rebuild sees the removal instead of merely not finding the domain.
- The publication goes through the **transactional outbox** ([ADR 0009](0009-transactional-outbox-with-spring-modulith.md)):
  the event is stored next to the site in one transaction and sent after it commits.
- **Each `ingest` instance consumes the whole topic in a consumer group of its own**, seeking to the beginning on
  every assignment. This is state every instance needs in full, not work to divide between them, so committed
  offsets could only ever cause one instance to miss something. Re-reading costs one record per tracked domain.
- **The list is loaded before `ingest` serves anything.** A blocking read of the whole topic runs during startup;
  until it finishes, the container is simply not ready and the reverse proxy sends it no traffic. Applying a
  record twice changes nothing, so the listener that follows afterwards needs no coordination with the load.
- Redis stays, as a **derived** lookup: it is what makes the per-event check cheap, and it is rebuilt rather than
  trusted.
- The outbox table lives in a schema of its own, `platform`, because it is neither bounded context's data
  ([ADR 0003](0003-schema-ownership-and-database-roles.md) gives each context a schema; this is the api service's
  own plumbing).

## Consequences
- Losing Redis entirely now degrades to a slower first start instead of silently dropping every event. An
  integration test proves it: the key is deleted, the load is run again, and the domain is accepted once more.
- Nothing is registered by hand any more. `infra/scripts/register-dev-site.sh` and the manual step in the
  deployment guide are gone.
- `ingest` became a Kafka consumer as well as a producer, and `api` became a producer. Both were already on the
  broker, so no new infrastructure appeared.
- A new schema cannot be created by the api role, and the role and schema script only runs when the database
  volume is first initialised. An existing deployment therefore needs one `CREATE SCHEMA platform` by hand, which
  the deployment guide records; until it is run, `api` refuses to start with a Flyway error. A loud failure was
  preferred to putting the outbox in a context's schema where it does not belong.
- Records for removed domains stay in the compacted log forever, because compaction keeps the last record per key
  and that record is the removal. At one small record per domain ever registered, this is not worth a tombstone
  and the deletion semantics it would bring.
- A registered domain reaches `ingest` a moment after the dashboard confirms it: the outbox sends after the
  commit, and the consumer then applies it. Events sent in between are rejected. Nothing is lost that was not
  already lost — the snippet cannot have been installed yet.
