# 0009. Transactional outbox with Spring Modulith

- Status: Accepted (to be implemented with Site Management)
- Date: 2026-09-26

## Context
When a site is registered, `ingest` must learn about its domain. Saving the site and publishing an event to Kafka
are two separate operations: if the database commits and the publish fails, the site never receives data.

## Decision
Domain events leaving the `api` service go through a transactional outbox, using Spring Modulith's event
publication registry (JDBC) and event externalization to Kafka rather than a hand-written outbox table.
Example flow: `SiteRegistered` → outbox → Kafka → `ingest` adds the domain to its allow-list in Redis.

## Consequences
- An event is published if and only if the transaction that produced it commits; failed publications are retried.
- Consumers must tolerate duplicates.
- Until Site Management exists, domains are registered in Redis manually (see `docs/deployment.md`).
