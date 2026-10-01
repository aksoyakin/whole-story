-- Transactional outbox: Spring Modulith's event publication registry (ADR 0009).
-- The structure is the one Modulith 2.1 expects (its schemas/v2/schema-postgresql.sql), created here rather than
-- by the library so that Flyway stays the only thing that changes this database. Keep the two in step when
-- Modulith's structure version changes; its repository tells you by failing loudly on a missing column.
--
-- The table lives in a schema of its own because it is neither context's data: it is how the api service gets a
-- message out of a transaction. The schema is created by infra/postgres/init/01-roles-and-schemas.sh.

create table platform.event_publication
(
    id                     uuid                     not null,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);

create index event_publication_serialized_event_hash_idx
    on platform.event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx
    on platform.event_publication (completion_date);
