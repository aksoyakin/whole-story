# 0000. Record architecture decisions

- Status: Accepted
- Date: 2026-09-26

## Context
Whole Story is built to be read: by future contributors and by engineers evaluating the design. The reasoning
behind a decision is lost quickly, while the code only shows its outcome.

## Decision
Significant decisions are recorded as Architecture Decision Records in `docs/adr/`, one file per decision,
using a short Context / Decision / Consequences format. Records are never rewritten; a changed decision gets
a new record that supersedes the old one.

## Consequences
- Trade-offs, including the rejected alternatives, are documented next to the code.
- A decision that cannot be justified in a record is a signal to reconsider it.
