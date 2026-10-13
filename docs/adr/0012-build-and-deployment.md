# 0012. Build and deployment

- Status: Accepted
- Date: 2026-09-27

## Context
The project runs on a single VPS managed by Dokploy. Deployments should only ever ship tested artifacts, and the
server should not spend its resources compiling code.

## Decision
- GitHub Actions runs the backend suite (Maven, Testcontainers with real PostgreSQL, Kafka and Redis) and the
  frontend suite (Biome, Playwright in Chromium, Firefox and WebKit, type checks, Vitest).
- On `main`, CI builds the images whose inputs changed and pushes them to GitHub Container Registry, then
  triggers a redeploy through the Dokploy API. Dokploy's own autodeploy on push is off, because it would deploy
  before the images exist.
- **A push rebuilds only what it changed, and a push that changes no container does not deploy at all.** Pushes
  that touch nothing but Markdown or `docs/` do not start the workflow; of the rest, each image is rebuilt only
  when its own inputs moved. The three service images share the parent pom, the Maven wrapper, `event-contracts`,
  the service Dockerfile and `.dockerignore`; the web image owns `web/`, `tracker/` and the pnpm manifests. No
  service module depends on another, so a service's sources belong to it alone. When the comparison cannot be
  made at all — a force push, a rewritten history — everything is rebuilt: that way of being wrong costs CI
  minutes, the other way deploys a stale image silently.
- The reason is not CI minutes. Compose leaves a container alone when its image digest and configuration are
  both unchanged, so not rebuilding is what stops the container from being replaced — and replacing `ingest`
  costs about nineteen seconds during which its route is down and arriving events are lost for good, since the
  tracker never retries. A Spring Boot jar is not reproducible byte for byte, so rebuilding a service that did
  not change is not a no-op: it publishes a different digest for identical code and the restart happens anyway.
- A change to a file Dokploy reads at deploy time rather than at build time — the production Compose file, the
  Prometheus or Grafana configuration, the Postgres init script — deploys without building anything.
- Application services use `pull_policy: always` so a redeploy picks up the new `latest` images.
- Docker Compose, no Kubernetes: one node does not need an orchestrator.
- There is no official `eclipse-temurin:27` image yet; the service Dockerfile installs the Temurin 27 GA binaries
  from Adoptium and verifies their SHA-256 checksums.

## Consequences
- Every deployment is a tested image; a failed test never reaches production.
- The mapping from a path to an image is maintained by hand and nothing fails loudly when it is wrong: a new
  shared input that nobody adds to it would leave a service running stale code. It is deliberately coarse for
  that reason — whole directories and the few shared files, rather than anything clever.
- **A repository file that production reads is not updated by a deployment, and the mechanism is worth
  knowing.** Dokploy deletes and re-clones the checkout each time, while a bind mount is bound to the
  directory's inode as it was when the container was created. A container that keeps running therefore keeps
  reading the clone it started with, which no longer exists on disk. Measured rather than inferred: replacing
  a mounted directory makes the file vanish from inside a running container, a change written in place is
  visible immediately, and a plain restart is enough to pick the new clone up. So the Compose file itself does
  update on a deployment, because Compose reads the fresh clone — which is why adding Prometheus and Grafana
  as services worked — and everything mounted out of the repository does not, which is why adding a panel to
  the dashboard they mount did not. The failure is silent in the worst way: Grafana answered a provisioning
  reload with "config reloaded" and changed nothing, because the directory it re-read was the orphaned one.
  A change to a mounted file therefore needs that one container restarted. It costs nothing here — Grafana
  holds only sessions, Prometheus keeps its data in a volume — and it does not touch `ingest`.
- This narrows the window in which a deploy loses events rather than closing it. A push that does change `ingest`
  still costs those nineteen seconds, because Compose has no rolling replacement: `deploy.update_config` belongs
  to Swarm and is ignored here. Measured rather than assumed — two replicas declared with `order: start-first`
  went to zero running containers during an update. Closing the window properly means a different deployment
  model, which is a decision of its own and not this one.
- The Temurin checksums must be updated with each JDK patch release, until the official image can be used.
