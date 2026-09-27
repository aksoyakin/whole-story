# syntax=docker/dockerfile:1.7
# docker build -f infra/docker/web.Dockerfile .

FROM node:24-alpine AS build
RUN corepack enable
WORKDIR /src
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY tracker/package.json tracker/
COPY web/package.json web/
RUN --mount=type=cache,id=pnpm,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY tracker tracker
COPY web web
# The tracker is served by the web app at /js/ws.js.
RUN pnpm --filter @wholestory/tracker build \
 && mkdir -p web/public/js && cp tracker/dist/ws.js web/public/js/ws.js
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter @wholestory/web build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /src/web/.next/standalone ./
COPY --from=build --chown=app:app /src/web/.next/static ./web/.next/static
COPY --from=build --chown=app:app /src/web/public ./web/public
USER app
EXPOSE 3000
CMD ["node", "web/server.js"]
