-- A bounce is a reporting judgement, not a measured fact, so its definition lives in the read contract
-- instead of a stored column (ADR 0017). Redefining it then costs one view migration, applies to history
-- as well, and rewrites no partition.
--
-- A session bounces when the visitor did nothing meaningful: fewer than two pageviews and no custom event.
-- Replacing the view keeps its columns, types and privileges, so the contract with api does not change.

create or replace view analytics.api_sessions as
select session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
       duration_seconds,
       (pageviews < 2 and events = pageviews) as is_bounce,
       entry_page, exit_page,
       referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
       country_code, subdivision_code, city_geoname_id,
       browser, browser_version, os, os_version, device_type
from analytics.sessions;

-- Nothing references the stored column now; dropping it is a metadata change.
alter table analytics.sessions drop column is_bounce;
