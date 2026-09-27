# 0004. Kafka between ingest and processing

- Status: Accepted
- Date: 2026-09-26

## Context
Tracker requests must be answered immediately regardless of database load, traffic arrives in bursts (a site
reaching the front page of a news aggregator), and a processing bug must not lose data.

## Decision
- `ingest` publishes each event to the `raw-events` topic and returns `202 Accepted`; `processor` consumes in
  batches.
- The message key is `siteId:visitorHash`, so all events of one visitor land on one partition, in order. This is
  what makes single-consumer sessionization possible.
- Messages are versioned JSON records from the shared `event-contracts` module (`RawEventV1` with a
  `schemaVersion`). No schema registry at this scale.
- Kafka runs in KRaft mode from the official `apache/kafka` image; retention is three days.
- RabbitMQ is not used. A second broker would add operational cost without a problem to solve here.

## Consequences
- Ingestion latency is independent of the database; bursts are absorbed by the log.
- Events can be replayed within the retention window, which requires idempotent processing (see ADR 0006).
- JSON is larger than a binary format; Protobuf is the planned alternative if load tests show serialization cost.
