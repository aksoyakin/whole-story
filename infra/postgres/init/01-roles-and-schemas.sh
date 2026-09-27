#!/bin/sh
# Runs once, on first initialisation of the data volume.
# One database user per service; schema ownership enforces which service may write where (D-012, D-037).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE wholestory_api       LOGIN PASSWORD '${API_DB_PASSWORD}';
CREATE ROLE wholestory_processor LOGIN PASSWORD '${PROCESSOR_DB_PASSWORD}';

REVOKE ALL ON DATABASE ${POSTGRES_DB} FROM PUBLIC;
GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO wholestory_api, wholestory_processor;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA identity  AUTHORIZATION wholestory_api;
CREATE SCHEMA sites     AUTHORIZATION wholestory_api;
CREATE SCHEMA analytics AUTHORIZATION wholestory_processor;

-- api may only look into analytics; SELECT on the api_* views is granted by processor migrations.
GRANT USAGE ON SCHEMA analytics TO wholestory_api;

CREATE EXTENSION IF NOT EXISTS citext SCHEMA public;
SQL
