# 0011. The web app as a backend-for-frontend

- Status: Accepted
- Date: 2026-09-27

## Context
The dashboard needs data from `api`. Letting the browser call `api` directly would expose it to the internet and
require CORS and cross-subdomain cookie handling.

## Decision
- The browser talks only to the Next.js app. Server Components call `api` over the internal Docker network;
  `api` has no public route.
- Dashboard state (period, filters) lives in the URL, so every view is shareable and survives reloads.
- `api` publishes an OpenAPI document (springdoc). The committed `web/openapi.json` is the contract the web
  client's TypeScript types are generated from; `OpenApiContractTest` fails when it drifts from the API.
  Response fields are required by default.

## Consequences
- `api`, PostgreSQL, Kafka and Redis have no public exposure; only `web` and `ingest` are routed.
- API changes that break the frontend fail at build time instead of in production.
- Every dashboard request passes through the web server, which is an extra hop compared to direct calls.
