-- Region and city names, resolved by ingest in the same MaxMind lookup that produced the codes.
--
-- The codes stay: a map and a filter use `country_code` and `subdivision_code`, while a person reads the name.
-- Storing the name is what spares the reporting side a second copy of MaxMind's data purely to label a row.
-- Rows collected before this have no name and are reported as unknown; the geoname id is still there, so they
-- can be filled in later if that ever matters.

alter table analytics.events   add column subdivision_name text;
alter table analytics.events   add column city_name        text;
alter table analytics.sessions add column subdivision_name text;
alter table analytics.sessions add column city_name        text;

-- The api_* views are the contract with api (D-012). Columns may be appended; the existing ones keep their
-- place and their order, which is what makes this a compatible change rather than a new view.
create or replace view analytics.api_events as
select event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname,
       referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
       country_code, subdivision_code, city_geoname_id,
       browser, browser_version, os, os_version, device_type, props,
       subdivision_name, city_name
from analytics.events;

create or replace view analytics.api_sessions as
select session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
       duration_seconds,
       (pageviews < 2 and events = pageviews) as is_bounce,
       entry_page, exit_page,
       referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
       country_code, subdivision_code, city_geoname_id,
       browser, browser_version, os, os_version, device_type,
       subdivision_name, city_name
from analytics.sessions;
