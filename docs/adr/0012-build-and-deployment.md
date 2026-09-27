# 0012. Build and deployment

- Status: Accepted
- Date: 2026-09-27

## Context
The project runs on a single VPS managed by Dokploy. Deployments should only ever ship tested artifacts, and the
server should not spend its resources compiling code.

## Decision
- GitHub Actions runs the backend suite (Maven, Testcontainers with real PostgreSQL, Kafka and Redis) and the
  frontend suite (Biome, Playwright in Chromium, Firefox and WebKit, type checks, Vitest).
- On `main`, CI builds four images and pushes them to GitHub Container Registry, then triggers a redeploy through
  the Dokploy API. Dokploy's own autodeploy on push is off, because it would deploy before the images exist.
- Application services use `pull_policy: always` so a redeploy picks up the new `latest` images.
- Docker Compose, no Kubernetes: one node does not need an orchestrator.
- There is no official `eclipse-temurin:27` image yet; the service Dockerfile installs the Temurin 27 GA binaries
  from Adoptium and verifies their SHA-256 checksums.

## Consequences
- Every deployment is a tested image; a failed test never reaches production.
- The Temurin checksums must be updated with each JDK patch release, until the official image can be used.
