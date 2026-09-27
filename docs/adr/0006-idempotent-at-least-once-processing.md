# 0006. At-least-once delivery with idempotent writes

- Status: Accepted
- Date: 2026-09-26

## Context
Kafka delivers at least once: after a crash, a batch can be processed again. Inserting events is easy to make
idempotent, but counters (session page counts, hourly rollups) are incremented and would be doubled on replay.

## Decision
- Every event gets a UUIDv7 `eventId` in ingest.
- A batch is written in one transaction:
  1. `INSERT INTO events ... ON CONFLICT DO NOTHING RETURNING event_id`
  2. Session and rollup increments are derived **only from the returned, newly inserted events**.
- Offsets are committed after the transaction commits.
- A failing batch is retried with exponential backoff until it succeeds instead of being skipped. Malformed records,
  which can never succeed, are logged and skipped.

## Consequences
- Replays change nothing; an integration test sends the same events twice and checks events, sessions and
  rollups.
- If the database is down, processing pauses and resumes without data loss; Kafka buffers in the meantime.
