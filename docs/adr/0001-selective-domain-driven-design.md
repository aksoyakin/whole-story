# 0001. Selective Domain-Driven Design

- Status: Accepted
- Date: 2026-09-26

## Context
The system has two very different halves. Account and site management carry real business rules (a domain is
unique across the system, a site limit cannot be exceeded, an organization keeps at least one owner).
Event ingestion and reporting, which make up most of the code and load, are a data pipeline and a read model:
there are no invariants to protect, only throughput and query speed.

Applying tactical DDD everywhere would turn every pageview into an aggregate loaded and saved through a
repository, which is slow and adds nothing.

## Decision
- **Strategic DDD everywhere.** Bounded contexts: Identity & Access, Site Management, Ingestion, Analytics.
  A ubiquitous language is kept for all of them.
- **Tactical DDD only where rules live**: Identity & Access and Site Management use aggregates, value objects
  and domain events inside a hexagonal (ports and adapters) structure.
- **Ingestion is a pipeline**: small, single-purpose steps (validate, hash, enrich, sessionize, write).
- **Analytics is the read side of CQRS**: SQL-first queries over purpose-built tables, no aggregates.

## Consequences
- Business rules are explicit and unit-testable where they matter.
- The hot path stays simple and fast.
- Contributors need to know which style applies where; package documentation states it per context.
