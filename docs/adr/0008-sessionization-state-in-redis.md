# 0008. Session state in Redis

- Status: Accepted
- Date: 2026-09-26

## Context
A session ends after 30 minutes of inactivity. The processor needs the current session of a visitor to decide
whether an event continues it. Keeping that state in memory is fastest, but it is lost on restarts and moves
between instances when Kafka rebalances partitions; recomputing sessions in SQL at query time is expensive.

## Decision
Session state (`sessionId`, `startedAt`, `lastSeenAt`) is stored in Redis under `session:{siteId}:{visitorHash}`
with a 30 minute TTL. Each batch loads all keys it needs with one `MGET` and writes them back in one pipeline.

## Consequences
- Restarts and rebalances do not split sessions.
- One round trip per batch, not per event.
- `startedAt` is part of the sessions primary key, so it must round-trip without precision loss. An early version
  stored milliseconds while events carry microseconds, which created a new session row on every batch; it is now
  stored losslessly and covered by a regression test.
