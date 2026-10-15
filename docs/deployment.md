# Deployment

Whole Story runs as a single Docker Compose stack on a VPS managed by [Dokploy](https://dokploy.com).
CI builds and tests every commit; images are only built from `main` after all tests pass, and only the ones the
push actually changed.

```
git push main ─► GitHub Actions ─► tests (Maven + Testcontainers, Playwright, Vitest)
                                 ─► the changed images → ghcr.io/<owner>/whole-story-{ingest,processor,api,web}
                                 ─► Dokploy deploy API ─► docker compose pull && up
```

Rebuilding an image is what replaces its container, and replacing `ingest` loses the events that arrive while
its route is down, so a push rebuilds as little as it can. A push that changes only Markdown or `docs/` runs no
workflow at all; one that changes only the production Compose file or the Prometheus, Grafana or Postgres init
configuration deploys without building anything. See [ADR 0012](adr/0012-build-and-deployment.md).

## Topology

| Public URL | Routed to | Notes |
|---|---|---|
| `https://wholestory.world` | `web:3000` | Landing page, tracker at `/js/ws.js` |
| `https://app.wholestory.world` | `web:3000` | Dashboard |
| `https://wholestory.world/api/event` | `ingest:8081` | Event collection; longer Traefik rule wins over the web route |
| — | `api:8080` | Internal only; called by `web` on the Docker network (backend-for-frontend). Holds the user sessions in Redis (ADR 0018) |
| — | PostgreSQL, Kafka, Redis | Internal only, no published ports. All three services talk to Kafka: ingest and api publish, processor and ingest consume |
| `https://metrics.wholestory.world` | `grafana:3000` | Dashboards, behind Grafana's own login. Anonymous access and sign-up are both off |
| — | `prometheus:9090` | Internal only and never routed: no authentication, and it holds per-site traffic volumes |
| — | `api:9080`, `ingest:9081`, `processor:9082` | Internal only, and deliberately so: actuator runs on its own port per service, so `/actuator/health` and `/actuator/prometheus` cannot be reached through a public route (ADR 0024). Routing `ingest` must keep pointing at 8081 |

## One-time setup

### 1. DNS
Point three `A` records at the VPS: `wholestory.world`, `app.wholestory.world` and
`metrics.wholestory.world` (the dashboards; step 4).

### 2. Container images
CI pushes to GitHub Container Registry. New GHCR packages are private: either make the four
`whole-story-*` packages public, or add GHCR credentials under Dokploy → Registry.

### 3. Dokploy service
Create a **Compose** service:

- Provider: GitHub, this repository, branch `main`
- Compose path: `infra/docker-compose.prod.yml`
- Environment:

  ```
  IMAGE_PREFIX=ghcr.io/<owner>/whole-story
  IMAGE_TAG=latest
  POSTGRES_PASSWORD=<random>
  API_DB_PASSWORD=<random>
  PROCESSOR_DB_PASSWORD=<random>
  MAXMIND_ACCOUNT_ID=<account id>
  MAXMIND_LICENSE_KEY=<license key>
  MAIL_HOST=<smtp host>
  MAIL_USERNAME=<mailbox>
  MAIL_PASSWORD=<mailbox password>
  MAIL_FROM=<mailbox>
  GRAFANA_ADMIN_PASSWORD=<a long random password>
  ```

  Database passwords are applied only when the PostgreSQL volume is first initialised.
  The MaxMind credentials come from a free GeoLite2 account and are required: the `geoipupdate` sidecar refuses to
  start without them. `ingest` itself tolerates a missing database and stores events without a location.

### 4. Domains (Dokploy → Domains, HTTPS with Let's Encrypt)

| Service | Host | Path | Port |
|---|---|---|---|
| `web` | `wholestory.world` | `/` | 3000 |
| `web` | `app.wholestory.world` | `/` | 3000 |
| `ingest` | `wholestory.world` | `/api/event` (do not strip the path) | 8081 |
| `grafana` | `metrics.wholestory.world` | `/` | 3000 |

Nothing is routed to the management ports (`9080`–`9082`), and **Prometheus is never routed at all**: it has
no authentication of any kind, and these series say how much traffic every tracked site gets. It is scraped
from inside the network and read through Grafana, which has a login. Container healthchecks use the management
ports from inside the network; a route to them would publish the same thing without even a password.


### 5. Continuous deployment
CI redeploys through the Dokploy API after the images are pushed. Add three repository secrets
(GitHub → Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `DOKPLOY_URL` | Dokploy panel URL, e.g. `https://dokploy.example.com` (use HTTPS: the API key is sent with every deploy) |
| `DOKPLOY_API_KEY` | Dokploy → Settings → Profile → API/CLI → generate a token |
| `DOKPLOY_COMPOSE_ID` | The id at the end of the Compose service URL: `.../services/compose/<composeId>` |

Keep Dokploy's *Autodeploy* off: a push would otherwise deploy before CI has built the new images.
Without the secrets, CI still publishes images and skips the deploy step. Services use
`pull_policy: always`, so every deploy pulls the current `latest` images — and a pull that returns the digest
already running leaves that container untouched, which is what keeps an unrelated push from restarting `ingest`.

## Outgoing mail

`api` sends the password reset link over SMTP on port 465, implicit TLS (ADR 0021). All four `MAIL_*` variables
are required: a stack without them refuses to start, rather than accepting reset requests and swallowing them.
The mailbox has to exist at the mail host, and `MAIL_FROM` should be that same mailbox so SPF and DKIM hold.

To send from a local run, export the credentials first — `api` does not read `.env` itself:

```bash
set -a; . ./.env; set +a
mvn -pl services/api spring-boot:run
```

Deploying this for the first time replaces the session store with Spring Session's indexed repository, which
writes different keys: everyone signs in once more afterwards. That is expected, and is what makes it possible
to end every session of one person when their password is reset.

## GeoIP database

`ingest` resolves country, region and city before discarding the IP address. The GeoLite2 City database is licensed
and never committed; a `maxmindinc/geoipupdate` sidecar downloads it into the `geoip` volume and refreshes it every
72 hours, and `ingest` mounts that volume read-only. For local development, download it once:

```bash
infra/scripts/download-geoip.sh                     # credentials from .env, writes infra/geoip/
GEOIP_DATABASE=$PWD/infra/geoip/GeoLite2-City.mmdb mvn -pl services/ingest spring-boot:run
```

## Upgrading a database that already exists

`infra/postgres/init/01-roles-and-schemas.sh` only runs when the PostgreSQL volume is created, so a schema added
to it later has to be created by hand once on an existing database. The `platform` schema, which holds the
transactional outbox, was added after the first deployment:

```bash
psql -U postgres -d wholestory -c 'CREATE SCHEMA IF NOT EXISTS platform AUTHORIZATION wholestory_api;'
```

Without it `api` stops at startup with a Flyway error, which is the intended failure: loud rather than silent.

## Old months are dropped, and it cannot be undone

`processor` keeps monthly partitions of `analytics.events` and `analytics.sessions` in both directions: it
creates the next two months ahead of time, and it drops a month once all of it is older than the 24 months
of raw data the design keeps ([ADR 0007](adr/0007-events-sessions-and-additive-rollups.md)). It runs at
startup and daily at 03:00 UTC, and it names every partition it drops in the log before dropping it:

```
Dropping analytics.events_2026_09, covering 2026-09-01T00:00:00Z to 2026-10-01T00:00:00Z: entirely older
than 2028-10-01T00:00:00Z, the 24 months of raw data kept
Raw events and sessions now reach back to 2026-10-01
```

Three things follow for whoever operates this:

- **There is no recovery.** A dropped partition is gone, and the rollups built from it (`page_hourly`,
  `custom_event_hourly`, `visitor_daily`) are all that remains of those months. They are deliberately kept
  forever.
- **The horizon is published, and the reports obey it.** The second line above is written to
  `analytics.raw_data_retention`, and `api` refuses a report that begins before it with a `400` rather than
  answering it short — the two partial days at the edges of a range are counted from the raw data, so a range
  reaching past the horizon would quietly lose them. To see where it stands:

  ```sql
  select retained_from, updated_at from analytics.raw_data_retention;
  ```

- **A partition whose bounds the job does not recognise is left alone, and says so**, at `WARN`:
  `Leaving analytics.<name> alone: its bounds are not a range this job recognises`. That means hand-written
  DDL created it; nothing is lost, but it will never be dropped either, and `retained_from` ignores it.

## Clearing what an earlier purge left behind

`visitor_daily` was missing from the purge for as long as it has existed, so a database that removed a site
before this was fixed still holds that site's daily rollup rows ([ADR 0023](adr/0023-deleting-the-data-of-a-removed-site.md)).
The migration cannot clean them up: the processor owns the analytics schema but has no access to `sites`, so
it cannot tell which site ids were removed.

The topic can. `site-purge` is compacted and keyed on the site id, so it still holds one purge record for
every site ever removed — reading it from the beginning re-purges all of them, this time from every table.
The purge is idempotent by design, so this is safe to do at any point:

```bash
# In the Kafka container. Stop the processor first: a consumer group cannot be moved while a member holds it.
/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group processor --topic site-purge --reset-offsets --to-earliest --execute
```

Then start the processor and confirm from the database, counting **every** table keyed by site rather than
the four this check used to name:

```sql
select 'events' as t, count(*) from analytics.events where site_id = '<removed site>'
union all select 'sessions', count(*) from analytics.sessions where site_id = '<removed site>'
union all select 'page_hourly', count(*) from analytics.page_hourly where site_id = '<removed site>'
union all select 'custom_event_hourly', count(*) from analytics.custom_event_hourly where site_id = '<removed site>'
union all select 'visitor_daily', count(*) from analytics.visitor_daily where site_id = '<removed site>';
```

A fresh database needs none of this; the purge has covered all five tables since the day it was fixed.

## A mounted config file needs its container restarted

Grafana's dashboards and provisioning, and Prometheus' scrape configuration, are bind-mounted out of this
repository rather than baked into an image. Deploying a change to one of them is not enough to apply it.

Dokploy deletes and re-clones the checkout on every deployment, and a bind mount is bound to the directory's
inode as it stood when the container was created. A container that keeps running — and one whose image and
configuration did not change is deliberately left running — therefore keeps reading the clone it started
with, which no longer exists. Measured: replacing a mounted directory makes the file vanish from inside a
running container, while a change written in place is visible at once, and a restart is enough to pick the new
clone up.

**After changing a file under `infra/grafana/` or `infra/prometheus/`, restart that one container.** It costs
nothing: Grafana holds only sessions and Prometheus keeps its data in a volume, and neither restart touches
`ingest`. The Compose file itself is exempt, because Compose reads it from the fresh clone.

Expect no error if you forget. Grafana answers a provisioning reload with `config reloaded` and keeps serving
the old dashboard, because the directory it re-read is the orphaned one.

## Kafka topics are created at startup

Auto-creation is off on the broker, so every topic is declared as a `NewTopic` bean by the service that owns it:
`raw-events` by `ingest`, `site-events` and `site-purge` by `api` (ADR 0019, ADR 0023). Those beans run once, while the application context
starts.

Deleting a topic is therefore a two-step operation. A topic deleted while its owner is running is not recreated, and
the owner then fails to produce to it: `api` leaves the `SiteRegistered` publication in `platform.event_publication`
with no completion date, and `ingest` cannot hand over the events it collects. **After deleting a topic, redeploy.**
On the way back up the services recreate their topics, and `api` resends the publications that never completed
(`spring.modulith.events.republish-outstanding-events-on-restart`, ADR 0009).

## Starting over with empty data

Only the data is removed; the roles, schemas and volumes stay. Run these in the Dokploy container terminals, in
this order.

```sql
-- postgres, as the superuser: remove the data, keep the schema
truncate analytics.events, analytics.sessions, analytics.page_hourly, analytics.custom_event_hourly;
truncate sites.sites, sites.goals, sites.ip_exclusions, sites.page_exclusions, identity.memberships,
         identity.password_reset_tokens, identity.organizations, identity.users, platform.event_publication;
```

```bash
# kafka: drop all three, including the compacted ones that would otherwise replay old sites
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic raw-events
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic site-events
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic site-purge

# redis: sessionization state and the tracked-domain lookup, plus the user sessions
redis-cli FLUSHALL
```

Then redeploy, which is what brings the topics back, and register again from the dashboard. Verify in this order:

```bash
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list   # all three topics present
redis-cli HGETALL sites:domains                                          # domain → the new site id
```

```sql
select event_type, completion_date from platform.event_publication;      -- completion date is set
```

## Base images

There is no official `eclipse-temurin:27` image yet. `infra/docker/service.Dockerfile` installs the
Temurin 27 GA binaries from Adoptium and verifies their SHA-256 checksums (amd64 and arm64).
