-- Seeds a realistic analytics corpus, so that the read side can be measured against something that looks
-- like a site with history rather than against an empty table (docs/performance.md).
--
-- Run through seed-analytics.sh, which supplies the parameters below. Local only: it writes directly into
-- analytics, which in production only the processor may do (D-012).
--
-- Why the corpus cannot be produced by the load generator instead: ingest stamps the event time from the
-- server clock (D-027), so k6 can only ever write "now". A dashboard question is about the last week or the
-- last month, and against twenty minutes of traffic every such query reads almost nothing.
--
-- What is modelled, and what is not:
--
--   visitor_hash  unique per site *per day*, and that is the single most important detail here. Production
--                 hashes hash(dailySalt + siteId + ip + userAgent) and rotates the salt every day (ADR 0005),
--                 so the same person is a different hash tomorrow. "Unique visitors over 30 days" is
--                 therefore close to the sum of thirty daily figures, and the distinct count the query has to
--                 do is correspondingly large. Modelling a visitor as stable across the period would have
--                 made every COUNT(DISTINCT) far cheaper than the real one.
--   skew          paths, countries, cities and sources follow a steep distribution rather than a flat one:
--                 a breakdown orders by visitors and takes the top rows, and with uniform data every row
--                 looks alike, which is both unrealistic and can change what the planner chooses.
--   coherence     events are generated from sessions and the rollups are aggregated from the events, so
--                 session_id always resolves, pageviews and events agree with the rows that exist, and the
--                 rollup equals what a count over events would give. The semi-join filters (D-104) join
--                 events to sessions by session_id, so incoherent ids would have measured an empty result.
--   time of day   uniform within a day. Real traffic has a daily shape; it would change which hour buckets
--                 are busy without changing what any query has to read, so it is not worth the complexity.
--   partitions    created here. PartitionMaintenance only keeps the previous, current and next two months
--                 (D-033) and there is no DEFAULT partition, so seeding history fails without this.
--
-- The primary keys are monotonic rather than random, like the UUIDv7s production generates, so the key
-- indexes fill in order here as they do there.

\set ON_ERROR_STOP on
\timing on

create temp table seed_params as
select :months::int                  as months,
       :main_events::bigint          as main_events,
       :neighbour_events::bigint     as neighbour_events,
       :neighbours::int              as neighbours,
       :'main_domain'::text          as main_domain,
       -- Shapes held constant so the row counts stay predictable from the event budget.
       3.0::numeric                  as events_per_session,
       1.5::numeric                  as sessions_per_visitor;

-- ---------------------------------------------------------------------------------------------------------
-- Pools. Small, fixed vocabularies, the way real dimensions behave: a site has a few hundred pages, not a
-- different one on every hit.
-- ---------------------------------------------------------------------------------------------------------

create temp table seed_paths as
select i as i,
       case i when 0 then '/' when 1 then '/pricing' when 2 then '/docs' when 3 then '/blog'
              else '/blog/post-' || i end as path
from generate_series(0, 499) i;

create temp table seed_countries (i int, code text, sub text, sub_name text, city_id int, city text);
insert into seed_countries
select i,
       -- The first fifteen are real so the top of a breakdown reads like a real one; the rest are synthetic
       -- two-letter codes purely to give the dimension the cardinality a real site sees. Flattening 200 pool
       -- rows onto 15 codes, which is what this did first, made the Countries breakdown fifteen rows wide.
       case when i < 15
            then (array['TR','US','DE','GB','FR','NL','ES','IT','PL','SE','BR','IN','JP','CA','AU'])[1 + i]
            else chr(65 + (i / 26)) || chr(65 + (i % 26)) end,
       null, null, null, null
from generate_series(0, 199) i;
-- Region and city are present for most rows and absent for some: a VPN or Apple Private Relay resolves to a
-- country and no further, and that row is reported rather than dropped (D-130).
update seed_countries
set sub = code || '-' || (array['01','06','34','07','16'])[1 + i % 5],
    sub_name = (array['Capital','Coastal','Northern','Southern','Central'])[1 + i % 5] || ' Region',
    city_id = 2000000 + i * 37,
    city = (array['Istanbul','Ankara','Berlin','London','Paris','Amsterdam','Madrid','Rome'])[1 + i % 8]
where i % 7 <> 0;

create temp table seed_sources as
select i, (array['Google','Bing','DuckDuckGo','Twitter','Reddit','Hacker News','LinkedIn','Facebook',
                 'YouTube','GitHub','Product Hunt','Lobsters','Mastodon','Slack','Telegram'])[1 + i % 15]
          || case when i < 15 then '' else ' ' || i end as source
from generate_series(0, 49) i;

create temp table seed_clients (i int, browser text, browser_version text, os text, os_version text, device text);
insert into seed_clients
select i,
       (array['Chrome','Safari','Firefox','Edge','Samsung Internet','Opera'])[1 + i % 6],
       (array['140.0','141.0','139.0','18.4','17.6'])[1 + i % 5],
       (array['macOS','Windows','iOS','Android','Linux'])[1 + i % 5],
       (array['15.1','11','18.4','15','6.11'])[1 + i % 5],
       (array['desktop','mobile','tablet'])[1 + i % 3]
from generate_series(0, 19) i;

create temp table seed_campaigns as
select i, 'campaign-' || i as campaign,
       (array['newsletter','cpc','social','email'])[1 + i % 4] as medium,
       (array['launch','retarget','brand'])[1 + i % 3] as content
from generate_series(0, 9) i;

create temp table seed_custom_events as
select i, (array['Signup','Purchase','Contact','Download','Trial started'])[1 + i % 5] as name
from generate_series(0, 4) i;

-- ---------------------------------------------------------------------------------------------------------
-- Starting clean. The keys below are derived from a counter that restarts at zero, so a second run would
-- collide rather than add to the corpus. This is a measurement rig on a local machine; it is the one place
-- in this repository allowed to truncate analytics.
-- ---------------------------------------------------------------------------------------------------------

truncate analytics.events, analytics.sessions, analytics.page_hourly, analytics.custom_event_hourly;

-- ---------------------------------------------------------------------------------------------------------
-- Sites. The neighbours exist so that the site_id predicate has to discriminate: on a single-site table an
-- index range scan looks better than it ever would in production.
-- ---------------------------------------------------------------------------------------------------------

create temp table seed_sites (n int, site_id uuid, domain text, events bigint);
insert into seed_sites
select n,
       ('00000000-0000-7000-9000-' || lpad(to_hex(n), 12, '0'))::uuid,
       case when n = 0 then (select main_domain from seed_params)
            else 'neighbour-' || n || '.example' end,
       case when n = 0 then (select main_events from seed_params)
            else (select neighbour_events from seed_params) end
from generate_series(0, (select neighbours from seed_params)) n;

insert into sites.sites (id, organization_id, domain, timezone, public_dashboard, created_at, updated_at)
select site_id,
       '00000000-0000-7000-a000-000000000001'::uuid,
       domain,
       'Europe/Istanbul',
       -- Shared, because the measurement reads the public surface: the SQL is the same either way and only
       -- the source of authority differs (D-137), and this needs no session cookie.
       true,
       now() - interval '3 years',
       now()
from seed_sites
on conflict do nothing;

-- Two goals, so the conversion query is measured with something to match: one event goal and one page
-- pattern with a wildcard, which are the two shapes that exist (ADR 0022).
insert into sites.goals (id, site_id, type, event_name, page_path, created_at)
select ('00000000-0000-7000-b000-' || lpad(to_hex(n * 2), 12, '0'))::uuid,
       site_id, 'EVENT', 'Signup', null, now() - interval '3 years'
from seed_sites
on conflict do nothing;
insert into sites.goals (id, site_id, type, event_name, page_path, created_at)
select ('00000000-0000-7000-b000-' || lpad(to_hex(n * 2 + 1), 12, '0'))::uuid,
       site_id, 'PAGEVIEW', null, '/blog/*', now() - interval '3 years'
from seed_sites
on conflict do nothing;

-- ---------------------------------------------------------------------------------------------------------
-- Partitions for every month being seeded, plus the current one.
-- ---------------------------------------------------------------------------------------------------------

do $$
declare
    first_month date := date_trunc('month', now())::date
                        - ((select months from seed_params) || ' months')::interval;
    m date;
begin
    m := first_month;
    while m <= date_trunc('month', now() + interval '1 month')::date loop
        execute format(
            'create table if not exists analytics.events_%s partition of analytics.events
             for values from (%L) to (%L)',
            to_char(m, 'YYYY_MM'), m, (m + interval '1 month')::date);
        execute format(
            'create table if not exists analytics.sessions_%s partition of analytics.sessions
             for values from (%L) to (%L)',
            to_char(m, 'YYYY_MM'), m, (m + interval '1 month')::date);
        m := (m + interval '1 month')::date;
    end loop;
end $$;

-- ---------------------------------------------------------------------------------------------------------
-- The corpus, one (site, month) chunk at a time. Chunked because a single statement for fifty million rows
-- holds one transaction, one sort and one pile of WAL for the whole run.
-- ---------------------------------------------------------------------------------------------------------

do $$
declare
    p            record;
    s            record;
    m            int;
    month_start  timestamptz;
    month_days   int;
    day_cap      int;
    ev_per_month bigint;
    sess_count   bigint;
    visitors_day bigint;
    session_base bigint := 0;
    event_base   bigint := 0;
    inserted     bigint;
    started      timestamptz := clock_timestamp();
begin
    select * into p from seed_params;

    for s in select * from seed_sites order by n loop
        ev_per_month := greatest((s.events / p.months)::bigint, 1);

        for m in 0 .. p.months - 1 loop
            -- Ends on the current month rather than the previous one. The first version subtracted
            -- (months - m), which left the month we are in unseeded — so `today`, `7d` and most of `30d`,
            -- the periods the dashboard actually offers, would have measured an empty range and reported it
            -- as fast. Caught by reading the dates the dry run produced: they stopped at the end of last
            -- month.
            month_start := date_trunc('month', now()) - ((p.months - 1 - m) || ' months')::interval;
            month_days  := extract(day from (date_trunc('month', month_start + interval '1 month')
                                             - month_start));
            -- The current month is only partly elapsed, so traffic stops at today rather than running on
            -- into days that have not happened.
            day_cap     := case when month_start = date_trunc('month', now())
                                then extract(day from now())::int
                                else month_days end;
            sess_count  := greatest((ev_per_month / p.events_per_session)::bigint, 1);
            -- Distinct visitors per day, which is the grain the daily salt gives them.
            visitors_day := greatest((sess_count::numeric / day_cap / p.sessions_per_visitor)::bigint, 1);

            -- Sessions first: everything else is derived from them.
            -- Every random choice is drawn once per row in `draw` and the pools are then joined on a
            -- stable value. Writing `where i = floor(random() * n)` instead is the trap this hit first:
            -- random() is volatile, so it is re-evaluated for each row the pool scan looks at and each row
            -- is compared against its own fresh number. That matches zero rows more often than one, the
            -- cross join then eliminates the outer row, and the whole seed produced nothing — with no error.
            -- The joins are LEFT for the same reason: an index out of range should show up as a NULL in a
            -- NOT NULL column, loudly, rather than as a row that quietly disappears.
            with visits as materialized (
                -- Materialized, and over a FROM with one row per visit, which is the whole point: the first
                -- version drew these in a LATERAL that referenced nothing from the outer row, so it had no
                -- lateral dependency at all and the planner evaluated it ONCE for the statement. Every one of
                -- the thirty thousand visits in a chunk then came out identical — same day, same page, same
                -- country — and nothing complained. Measured, not reasoned about: the shape query reported
                -- two distinct paths for the whole corpus.
                select g,
                       floor(random() * day_cap)::int                   as day,
                       floor(random() * 86400)::int                     as secs,
                       floor(random() * visitors_day)::int              as visitor,
                       -- Steep: most visits are one page, a few are long.
                       1 + floor(power(random(), 3) * 9)::int            as pageviews,
                       case when random() < 0.08 then 1 else 0 end      as extra_events,
                       (10 + floor(power(random(), 2) * 600))::int       as duration,
                       floor(power(random(), 3) * 500)::int             as entry_i,
                       floor(power(random(), 3) * 500)::int             as exit_i,
                       -- A third of visits have no external source to name: typed, bookmarked, or internal
                       -- navigation, which is information rather than missing data (ADR 0015, D-112).
                       case when random() < 0.33 then null
                            else floor(power(random(), 2) * 50)::int end as source_i,
                       floor(random() * 10)::int                        as campaign_i,
                       floor(power(random(), 3) * 200)::int             as geo_i,
                       floor(power(random(), 2) * 20)::int              as client_i,
                       -- UTM is mostly absent, as it is in reality.
                       random() < 0.12                                  as utm
                from generate_series(1, sess_count) g
            ),
            visits_at as materialized (
                select v.*,
                       month_start + (v.day || ' days')::interval
                                   + (v.secs || ' seconds')::interval as started_at
                from visits v
            )
            insert into analytics.sessions (
                session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                entry_page, exit_page, referrer, referrer_source,
                utm_source, utm_medium, utm_campaign, utm_content, utm_term,
                country_code, subdivision_code, subdivision_name, city_geoname_id, city_name,
                browser, browser_version, os, os_version, device_type)
            select ('00000000-0000-7000-8000-' || lpad(to_hex(session_base + g), 12, '0'))::uuid,
                   draw.started_at,
                   s.site_id,
                   -- Keyed on the calendar date, not on the day within the month. Using the latter, which
                   -- is what this did first, gave the same hash to the same day number in every month and so
                   -- let a visitor recur across the period — the one thing the daily salt rules out (D-028).
                   -- Caught by counting: 61 days held 21,605 distinct hashes but 34,689 (day, hash) pairs.
                   hashtextextended(s.site_id::text || ':' || draw.started_at::date::text
                                    || ':' || draw.visitor::text, 0),
                   -- Clamped inside the month so every event of a visit lands in the partition this chunk
                   -- rolls up; a visit starting at 23:59 on the last day would otherwise put rows outside
                   -- the range and the rollup would stop agreeing with the events.
                   least(draw.started_at + (draw.duration || ' seconds')::interval,
                         month_start + interval '1 month' - interval '1 second',
                         now()),
                   draw.pageviews,
                   draw.pageviews + draw.extra_events,
                   en.path,
                   -- A single-pageview visit entered and left on the same page.
                   case when draw.pageviews = 1 then en.path else ex.path end,
                   case when src.source is null then null
                        else 'https://' || lower(replace(src.source, ' ', '')) || '.example/' end,
                   src.source,
                   case when draw.utm then 'newsletter' end,
                   case when draw.utm then camp.medium end,
                   case when draw.utm then camp.campaign end,
                   case when draw.utm then camp.content end,
                   null,
                   geo.code, geo.sub, geo.sub_name, geo.city_id, geo.city,
                   cl.browser, cl.browser_version, cl.os, cl.os_version, cl.device
            from visits_at draw
            left join seed_paths     en   on en.i   = draw.entry_i
            left join seed_paths     ex   on ex.i   = draw.exit_i
            left join seed_sources   src  on src.i  = draw.source_i
            left join seed_campaigns camp on camp.i = draw.campaign_i
            left join seed_countries geo  on geo.i  = draw.geo_i
            left join seed_clients   cl   on cl.i   = draw.client_i;

            -- Events from the sessions just written: the first pageview lands on the entry page, the last on
            -- the exit page, and anything past the pageview count is a custom event.
            insert into analytics.events (
                event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname,
                referrer, referrer_source, utm_source, utm_medium, utm_campaign, utm_content, utm_term,
                country_code, subdivision_code, subdivision_name, city_geoname_id, city_name,
                browser, browser_version, os, os_version, device_type, props)
            select ('00000000-0000-7000-8001-' || lpad(to_hex(event_base + row_number() over ()), 12, '0'))::uuid,
                   -- Spread across the visit, and never past its end.
                   ses.started_at + ((ev.n - 1)::double precision / greatest(ses.events, 1)
                                     * (ses.ended_at - ses.started_at)),
                   ses.site_id,
                   ses.session_id,
                   ses.visitor_hash,
                   case when ev.n <= ses.pageviews then 'pageview' else ce.name end,
                   s.domain,
                   case when ev.n = 1              then ses.entry_page
                        when ev.n = ses.pageviews  then ses.exit_page
                        when ev.n <= ses.pageviews then mid.path
                        else ses.exit_page end,
                   ses.referrer, ses.referrer_source,
                   ses.utm_source, ses.utm_medium, ses.utm_campaign, ses.utm_content, ses.utm_term,
                   ses.country_code, ses.subdivision_code, ses.subdivision_name,
                   ses.city_geoname_id, ses.city_name,
                   ses.browser, ses.browser_version, ses.os, ses.os_version, ses.device_type,
                   null
            from analytics.sessions ses
            -- Named rather than left as the bare alias: the pool tables also have a column called i,
            -- and an ambiguous reference is the error this produced first.
            cross join generate_series(1, ses.events) as ev(n)
            cross join lateral (
                -- `from (values (ev.n))` is what makes this a real lateral. Without a reference to the outer
                -- row the planner is free to evaluate it once for the whole statement, which is exactly what
                -- happened the first time.
                select floor(power(random(), 3) * 500)::int as mid_i,
                       floor(random() * 5)::int             as ce_i
                from (values (ev.n)) as dep(seq)
            ) draw
            left join seed_paths         mid on mid.i = draw.mid_i
            left join seed_custom_events ce  on ce.i  = draw.ce_i
            where ses.site_id = s.site_id
              and ses.started_at >= month_start
              and ses.started_at < month_start + interval '1 month';

            get diagnostics inserted = row_count;
            event_base   := event_base + inserted;
            session_base := session_base + sess_count;

            -- Rollups aggregated from what was just written, so they agree with the events by construction.
            insert into analytics.page_hourly (site_id, hour, pathname, pageviews)
            select site_id, date_trunc('hour', timestamp), pathname, count(*)
            from analytics.events
            where site_id = s.site_id
              and timestamp >= month_start and timestamp < month_start + interval '1 month'
              and name = 'pageview'
            group by 1, 2, 3
            on conflict (site_id, hour, pathname)
                do update set pageviews = page_hourly.pageviews + excluded.pageviews;

            insert into analytics.custom_event_hourly (site_id, hour, event_name, count)
            select site_id, date_trunc('hour', timestamp), name, count(*)
            from analytics.events
            where site_id = s.site_id
              and timestamp >= month_start and timestamp < month_start + interval '1 month'
              and name <> 'pageview'
            group by 1, 2, 3
            on conflict (site_id, hour, event_name)
                do update set count = custom_event_hourly.count + excluded.count;

            raise notice '% | % | month %/% | % events | %s elapsed',
                to_char(clock_timestamp(), 'HH24:MI:SS'), s.domain, m + 1, p.months, inserted,
                round(extract(epoch from clock_timestamp() - started));
        end loop;
    end loop;
end $$;

-- The planner needs statistics before any of this can be measured; without them the first query measures
-- a bad plan rather than the schema.
vacuum analyze analytics.events;
vacuum analyze analytics.sessions;
vacuum analyze analytics.page_hourly;
vacuum analyze analytics.custom_event_hourly;

-- ---------------------------------------------------------------------------------------------------------
-- What was produced. Printed rather than assumed: a corpus whose shape nobody checked cannot support a claim.
-- ---------------------------------------------------------------------------------------------------------

select 'events' as relation, count(*) as rows, min(timestamp)::date as first, max(timestamp)::date as last
from analytics.events
union all
select 'sessions', count(*), min(started_at)::date, max(started_at)::date from analytics.sessions
union all
select 'page_hourly', count(*), min(hour)::date, max(hour)::date from analytics.page_hourly
union all
select 'custom_event_hourly', count(*), min(hour)::date, max(hour)::date from analytics.custom_event_hourly;

-- Summed over the partitions: asking a partitioned parent for its size returns zero, which is what this
-- reported first.
select pg_size_pretty(sum(pg_total_relation_size(c.oid)) filter (where c.relname like 'events%'))   as events_size,
       pg_size_pretty(sum(pg_total_relation_size(c.oid)) filter (where c.relname like 'sessions%')) as sessions_size,
       pg_size_pretty(pg_database_size(current_database()))                                         as database_size
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'analytics' and c.relkind in ('r', 'p');
