# 0010. A pure domain layer and manual mappers

- Status: Accepted
- Date: 2026-09-26

## Context
Framework annotations in domain classes (persistence, serialization, validation) couple business rules to
infrastructure and make them harder to test and change.

## Decision
- Classes in `..domain..` packages may depend only on `java.*` and Lombok. An ArchUnit test (`DomainPurityTest`)
  fails the build otherwise.
- JPA entities, REST DTOs and messages are separate types in the infrastructure layer.
- Conversions between them are written by hand; no mapping library (MapStruct, ModelMapper).

## Consequences
- The domain model can be unit-tested without Spring.
- More code for mapping, in exchange for mappings that are explicit, debuggable and checked by the compiler.
