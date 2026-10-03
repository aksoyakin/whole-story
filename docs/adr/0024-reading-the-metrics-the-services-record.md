# 0024. Reading the metrics the services record

- Status: Accepted
- Date: 2026-10-03

## Context
Twice, a failure was deliberately moved out of the health endpoint and into a metric.

A missing GeoIP database was the first ([ADR 0013](0013-geoip-lookups-and-database-distribution.md), D-059). Health
is what restarts a
container, and a container that is accepting events perfectly well must not be restarted because a location
lookup is unavailable; the event is still collected, it just has no city on it.

A mail server that cannot be reached was the second (D-127). Adding the mail starter brought Boot's mail health
indicator with it, and that indicator opens an SMTP connection every time health is read — with a ten-second
container check, eight thousand connections a day to deliver a handful of messages, and an api marked unhealthy
whenever the mail host blinked. It was switched off, and what replaced it was a sentence: a send that fails
leaves its publication row incomplete, which is the honest record of it.

Both decisions were sound and both were half-finished, because **nothing could read a metric.** The three
services each asked for `prometheus` in `management.endpoints.web.exposure.include`, but no registry was on the
classpath, so the endpoint did not exist. Nothing failed. The only trace was one line at startup:

```
o.s.b.a.e.web.EndpointLinksResolver : Exposing 2 endpoints beneath base path '/actuator'
```

Two, where the configuration named three. This is the same shape as the session property that sat unapplied for
weeks because Boot 4 had renamed it (D-119): naming a thing that does not exist is silent.

So `geoip.database.loaded` has been recorded since M2 and never once been looked at, and the incomplete-outbox
row that replaced the mail health check had no reader at all.

## Decision

### A registry, and the endpoint on a port of its own
`micrometer-registry-prometheus` (Apache-2.0) is added to `ingest`, `processor` and `api`. Actuator then moves
to its own port, one per service, mirroring the service port: `9080`, `9081`, `9082`.

The separate port is the security half of this decision. `ingest` is the one service a public route points at,
and `/actuator/prometheus` tells whoever reads it how much traffic every tracked site gets, which domains exist
and why events are being rejected. On the service port that is one routing change away from being public; on a
port that nothing outside the Docker network can reach, it is not. The ports mirror the service ports rather
than sharing one number because all three run side by side on a developer's machine, and a port collision is
silent in exactly the way this project has been bitten by before.

Moving actuator also moves `/actuator/health`, so every container healthcheck in
`infra/docker-compose.prod.yml` moves with it. A healthcheck left on the old port would make the container
permanently unhealthy and stop the whole stack from starting.

### HTTP latency histograms are switched on
`management.metrics.distribution.percentiles-histogram.http.server.requests: true`. Without it the HTTP timer
publishes count, sum and max — enough for an average, useless for the question a load test asks, which is how
slow the slow tenth was. This was verified by scraping before and after rather than assumed, because the
property prefix is exactly the kind of thing Boot 4 renames.

### One new meter: publications that never went out
`outbox.publications.incomplete`, a gauge over
`select count(*) from platform.event_publication where completion_date is null`.

This is the reader D-127 never got. Resubmission runs every five minutes, so a number that stays above zero for
longer than that means the retry is not getting through either: a password
reset mail nobody received, or a `SiteRegistered` announcement that `ingest` never heard — which is precisely
the production incident that led to the resubmission schedule being written.

It is read at scrape time rather than cached. The index on `completion_date` makes it a cheap lookup, and a
stale copy of this number would be worse than none. If the query fails the gauge reports `NaN` rather than
zero: zero would claim the queue is empty, which is the one lie that matters here.

### Prometheus and Grafana run in both places
Both are in `infra/docker-compose.yml` and in `infra/docker-compose.prod.yml`, with their configuration and
dashboards provisioned from the repository in each.

Production was held back until the one number that decides it was known. The VPS already runs four JVMs next
to Postgres, Kafka, Redis, the web app and Dokploy itself, and every Java container runs with
`-XX:+ExitOnOutOfMemoryError` — a JVM that runs out of memory does not degrade, it dies, so monitoring that
kills what it monitors was the risk worth measuring first. The host turned out to have 32 GB with 8 in use.
At that headroom the question answers itself, and running it only locally would have meant the two failures
this ADR exists for stayed invisible in the one place they matter.

The two scrape configurations are separate files because the targets genuinely differ: in production the
services are containers reached by name, locally they run on the host. The datasource and the dashboards are
the same files in both.

**Only Grafana is published.** It gets a host of its own, `metrics.wholestory.world`, and carries the login:
anonymous access and sign-up are both switched off explicitly rather than left at their defaults, and the
admin password is a required variable, so a missing one stops the whole stack rather than quietly starting an
open dashboard. Prometheus is never routed. It has no authentication of any kind, and its series say how much
traffic every tracked site gets; its admin API is switched off as well, since nothing needs to delete a series
or take a snapshot.

The dashboard is ordered by what the product promises rather than by what is easy to graph: the pipeline
first (events accepted, rejections by reason, crawlers dropped, consumer lag), then the two failures this ADR
exists for, then latency, and the JVMs last. Every panel carries a description saying why it is there, because
a dashboard whose panels need explaining in conversation is not a deliverable.

Dashboards are files, provisioned read-only. One clicked together inside the container cannot be reviewed and
disappears with it.

## Consequences
- The endpoint is tested over real HTTP in each service, including the part that matters most: that the port
  the application is served on does not hand out metrics. `api` refuses that request with 401 rather than 404
  because its security chain runs before routing, so the test asserts the property, not the status code.
- Every metric name in the dashboard was read off a live scrape rather than written from memory. The Kafka
  consumer lag series, `kafka_consumer_fetch_manager_records_lag_max`, is auto-registered by Boot and was
  confirmed present before a panel was pointed at it.
- The rejection counter carries a `reason` label whose seven values are all fixed strings. Checked on purpose:
  a label fed from user input — a domain, a path — is how a Prometheus instance runs out of memory.
- Grafana OSS is AGPL-3.0. It is run as a separate container and nothing links to it, so it places no
  obligation on this project's source. `micrometer-registry-prometheus` and Prometheus itself are Apache-2.0,
  both compatible with this project's GPL-3.0.
- Production needs two things a deploy cannot create by itself: an `A` record for `metrics.`, and
  `GRAFANA_ADMIN_PASSWORD` in Dokploy's environment. The second is `:?required`, so forgetting it fails the
  deploy loudly instead of publishing an unauthenticated dashboard.
- Grafana keeps a volume for its own database — who is logged in, saved preferences. The dashboards come from
  files on every start, so losing that volume costs a session and nothing else.
- Still open: the roughly one hundred lines of configuration dump Kafka's admin client and Spring Data Redis
  write at INFO on every start. A log nobody can read is its own observability problem.
