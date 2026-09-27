-- Analytics schema, owned by processor (D-012). Rationale: docs/adr (data model ADRs).

create table analytics.events (
    event_id         uuid        not null,
    timestamp        timestamptz not null,
    site_id          uuid        not null,
    session_id       uuid        not null,
    visitor_hash     bigint      not null,
    name             text        not null,
    hostname         text        not null,
    pathname         text        not null,
    referrer         text,
    referrer_source  text,
    utm_source       text,
    utm_medium       text,
    utm_campaign     text,
    utm_content      text,
    utm_term         text,
    country_code     char(2),
    subdivision_code text,
    city_geoname_id  int,
    browser          text,
    browser_version  text,
    os               text,
    os_version       text,
    device_type      text,
    props            jsonb,
    primary key (event_id, timestamp)
) partition by range (timestamp);

create index events_site_time_idx on analytics.events (site_id, timestamp);

create table analytics.sessions (
    session_id       uuid        not null,
    started_at       timestamptz not null,
    site_id          uuid        not null,
    visitor_hash     bigint      not null,
    ended_at         timestamptz not null,
    pageviews        int         not null,
    events           int         not null,
    duration_seconds int generated always as (extract(epoch from ended_at - started_at)::int) stored,
    is_bounce        boolean generated always as (pageviews <= 1) stored,
    entry_page       text        not null,
    exit_page        text        not null,
    referrer         text,
    referrer_source  text,
    utm_source       text,
    utm_medium       text,
    utm_campaign     text,
    utm_content      text,
    utm_term         text,
    country_code     char(2),
    subdivision_code text,
    city_geoname_id  int,
    browser          text,
    browser_version  text,
    os               text,
    os_version       text,
    device_type      text,
    primary key (session_id, started_at)
) partition by range (started_at);

create index sessions_site_time_idx on analytics.sessions (site_id, started_at);

-- Rollups hold additive metrics only (D-030). Unique visitors are never summed across hours.
create table analytics.page_hourly (
    site_id   uuid        not null,
    hour      timestamptz not null,
    pathname  text        not null,
    pageviews bigint      not null,
    primary key (site_id, hour, pathname)
);

create table analytics.custom_event_hourly (
    site_id    uuid        not null,
    hour       timestamptz not null,
    event_name text        not null,
    count      bigint      not null,
    primary key (site_id, hour, event_name)
);

-- Read contract for the api service. Columns of these views must stay backwards compatible.
create view analytics.api_events as
select event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname,
       referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
       country_code, subdivision_code, city_geoname_id,
       browser, browser_version, os, os_version, device_type, props
from analytics.events;

create view analytics.api_sessions as
select session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
       duration_seconds, is_bounce, entry_page, exit_page,
       referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
       country_code, subdivision_code, city_geoname_id,
       browser, browser_version, os, os_version, device_type
from analytics.sessions;

create view analytics.api_page_hourly as
select site_id, hour, pathname, pageviews
from analytics.page_hourly;

create view analytics.api_custom_event_hourly as
select site_id, hour, event_name, count
from analytics.custom_event_hourly;

grant select on analytics.api_events, analytics.api_sessions,
                analytics.api_page_hourly, analytics.api_custom_event_hourly to wholestory_api;
