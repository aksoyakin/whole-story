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
| — | `api:8080` | Internal only; called by `web` on the Docker network (backend-for-frontend) |
| — | PostgreSQL, Kafka, Redis | Internal only, no published ports |

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
  ```

  Database passwords are applied only when the PostgreSQL volume is first initialised.

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

## Registering a site (until Site Management ships)

Ingest only accepts events for registered domains. Until sites can be created in the dashboard,
register one from the Dokploy terminal of the `redis` container:

```bash
redis-cli HSET sites:domains example.com "$(cat /proc/sys/kernel/random/uuid)"
```

## Base images

There is no official `eclipse-temurin:27` image yet. `infra/docker/service.Dockerfile` installs the
Temurin 27 GA binaries from Adoptium and verifies their SHA-256 checksums (amd64 and arm64).
