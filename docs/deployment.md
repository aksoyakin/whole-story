# Deployment

Whole Story runs as a single Docker Compose stack on a VPS managed by [Dokploy](https://dokploy.com).
CI builds and tests every commit; images are only built from `main` after all tests pass.

```
git push main ─► GitHub Actions ─► tests (Maven + Testcontainers, Playwright, Vitest)
                                 ─► images → ghcr.io/<owner>/whole-story-{ingest,processor,api,web}
                                 ─► Dokploy deploy webhook ─► docker compose pull && up
```

## Topology

| Public URL | Routed to | Notes |
|---|---|---|
| `https://wholestory.world` | `web:3000` | Landing page, tracker at `/js/ws.js` |
| `https://app.wholestory.world` | `web:3000` | Dashboard |
| `https://wholestory.world/api/event` | `ingest:8081` | Event collection; longer Traefik rule wins over the web route |
| — | `api:8080` | Internal only; called by `web` on the Docker network (backend-for-frontend). Holds the user sessions in Redis (ADR 0018) |
| — | PostgreSQL, Kafka, Redis | Internal only, no published ports. All three services talk to Kafka: ingest and api publish, processor and ingest consume |

## One-time setup

### 1. DNS
Point two `A` records at the VPS: `wholestory.world` and `app.wholestory.world`.

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
`pull_policy: always`, so every deploy pulls the current `latest` images.

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
