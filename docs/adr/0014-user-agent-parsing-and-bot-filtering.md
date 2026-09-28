# 0014. User-Agent parsing and bot filtering

- Status: Accepted
- Date: 2026-09-28

## Context
Events need the visitor's browser, operating system and device type, and a large share of any site's raw traffic is
crawlers, monitors and scrapers. Counting them as visitors makes the product's central number wrong.

Both jobs come from the same input, the User-Agent string, and both need a rule set that keeps up with new browsers
and new crawlers. A hand-written parser would be small but would quietly drift out of date, which for an analytics
product is a direct loss of accuracy.

## Decision
- The `processor` service parses the User-Agent with **Yauaa**, requesting only the five values that are stored
  (`DeviceClass`, `AgentName`, `AgentVersion`, `OperatingSystemName`, `OperatingSystemVersion`) and caching 10 000
  results. Limiting the fields limits the rule set: the measured footprint is about **34 MB of heap**, against the
  ~114 MB the library needs when every field is extracted.
- Bots are identified with Yauaa's own `UserAgentClassifier.isHuman`, which also covers missing and nonsensical
  User-Agents. Headless browsers are rejected on top of that: Yauaa reports them as an ordinary desktop, but a
  scraper driving a real browser is not a visit.
- **Bot events are dropped in the enrichment stage, before sessionization.** They never reach `events`, `sessions`,
  the rollups or the session state in Redis, so no dashboard query has to remember to exclude them and the rollups
  do not need a bot dimension. The number of dropped events is published as
  `events.dropped{reason="bot"}` so the loss is visible rather than silent.
- `device_type` keeps the three values a dashboard distinguishes (`desktop`, `mobile`, `tablet`); a TV, watch or
  console is stored as unknown rather than being forced into one of them.
- Yauaa's `??` marker for an undeterminable field is normalised to `NULL`.

## Consequences
- Bot traffic is gone for good: it cannot be analysed later, and a mistake in the classifier silently loses real
  visits. The counter is the safeguard that makes the volume observable, and the classifier is covered by tests
  built from real User-Agent strings.
- Visitor numbers drop when this ships, because crawler traffic was previously counted.
- The parser is a singleton: building it takes about 0.5 s at startup, plus 0.3 s to initialise on the first parse.
- The raw User-Agent travels through Kafka, unlike the IP address, because parsing is CPU-bound work that belongs in
  `processor` rather than in `ingest` (see [ADR 0002](0002-services-split-by-scaling-profile.md)). It is never
  written to the database and expires with the topic's three day retention
  (see [ADR 0005](0005-privacy-by-design.md)).
- Yauaa also understands User-Agent Client Hints, which matters as browsers freeze their User-Agent strings. The
  tracker does not forward those headers yet; doing so later needs no change to this decision.
