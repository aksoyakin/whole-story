# 0003. One PostgreSQL, a schema per context, enforced by database roles

- Status: Accepted
- Date: 2026-09-26

## Context
Separate database servers per service would be expensive to run for this project, but a shared database
tends to become a hidden integration point where any service reads and writes any table.

## Decision
- One PostgreSQL instance with a schema per context: `identity`, `sites` (owned by `api`) and `analytics`
  (owned by `processor`).
- Each service connects with its own role. Schema ownership decides who may run migrations and write.
- `api` reads analytics data only through `analytics.api_*` views. The views are the contract between the two
  services: `processor` may change its tables freely but must keep the view columns compatible.
- No foreign keys cross context boundaries (for example `sites.organization_id`); consistency between contexts
  is maintained through events.

## Consequences
- The rule "a service writes only its own schema" is enforced by PostgreSQL, not by convention; a test checks
  that the `api` role can select from exactly the four contract views.
- A breaking change to analytics data requires a new view version (for example `api_sessions_v2`).
- Cross-context integrity is eventual rather than enforced by the database.
