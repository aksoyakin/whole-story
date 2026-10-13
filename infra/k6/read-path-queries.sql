-- Times the queries behind the dashboard, against the corpus seed-analytics.sql builds
-- (docs/performance.md). Run with read-path-queries.sh.
--
-- These are the statements JdbcStatsQueries sends, reduced to what makes them expensive and with the site and
-- the period as parameters. They are measured here, one layer below the HTTP endpoints, because this is where
-- the decisions are: the question is not "how fast is /stats/summary" but which of the choices the schema
-- makes is the one that costs, and the answer has to name a query shape rather than a route.
--
-- What each pair is for:
--
--   summary          the open promise of ADR 0007. Unique visitors are counted exactly, with COUNT(DISTINCT)
--                    over sessions, because they are not additive and no rollup can hold them. The ADR says
--                    HyperLogLog is the next step "adopted on evidence rather than up front" — this is the
--                    evidence, or it is the reason not to.
--   pageviews        D-103. Unfiltered, pageviews come from the hourly rollup; filtered, the rollup holds no
--                    dimension beyond the page so the same number has to be counted from the events. Both
--                    shapes are timed over the same period, which is what turns "filtering costs more" from
--                    a design note into a number.
--   breakdown        the eight cards a dashboard draws. One pass over sessions for the dimensions that live
--                    there, and over events for the page, since a visit reads many pages (D-098).
--   semi-join        D-104, the two dimensions that cross the line between the tables: filtering visits by a
--                    page reaches them through their events, and filtering events by an entry page reaches
--                    them through their session. The most expensive shape in the system.
--   goals            every goal in one pass over the events, two expressions each (D-125), with a LIKE for
--                    the wildcard page target.
--
-- Postgres is deliberately untuned, because production is: the official image with no settings of its own,
-- so shared_buffers is 128 MB and work_mem is 4 MB. A number from a tuned database would describe a
-- deployment nobody runs.

\set ON_ERROR_STOP on
\timing on

-- Repeated so the reported time is a steady state rather than a first touch; the script runs the whole file
-- twice and the write-up reports the second pass as warm and the first as cold.
\set QUIET on
\pset pager off
\set QUIET off

\echo '=== 1. summary, 30 days, no filter (rollup supplies the pageviews) ==='
explain (analyze, buffers, costs off, timing off, summary on)
select v.visitors, v.visits, v.bounce_rate, v.avg_duration, p.pageviews
from (select count(distinct s.visitor_hash)     as visitors,
             count(*)                           as visits,
             coalesce(avg(s.is_bounce::int), 0) as bounce_rate,
             coalesce(avg(s.duration_seconds), 0) as avg_duration
      from analytics.api_sessions s
      where s.site_id = :'site' and s.started_at >= :'from' and s.started_at < :'to') v
cross join (select coalesce(sum(pageviews), 0) as pageviews
            from analytics.api_page_hourly
            where site_id = :'site' and hour >= :'from' and hour < :'to') p;

\echo '=== 2. summary, 30 days, one filter (pageviews now come from events, D-103) ==='
explain (analyze, buffers, costs off, timing off, summary on)
select v.visitors, v.visits, v.bounce_rate, v.avg_duration, p.pageviews
from (select count(distinct s.visitor_hash)     as visitors,
             count(*)                           as visits,
             coalesce(avg(s.is_bounce::int), 0) as bounce_rate,
             coalesce(avg(s.duration_seconds), 0) as avg_duration
      from analytics.api_sessions s
      where s.site_id = :'site' and s.started_at >= :'from' and s.started_at < :'to'
        and coalesce(s.country_code::text, '') = 'TR') v
cross join (select count(*) filter (where e.name = 'pageview') as pageviews
            from analytics.api_events e
            where e.site_id = :'site' and e.timestamp >= :'from' and e.timestamp < :'to'
              and coalesce(e.country_code::text, '') = 'TR') p;

\echo '=== 3. breakdown by source, 30 days (one pass over sessions) ==='
explain (analyze, buffers, costs off, timing off, summary on)
select coalesce(s.referrer_source::text, '') as key,
       null::text                            as label,
       count(distinct s.visitor_hash)         as visitors,
       count(*)                               as visits,
       coalesce(sum(s.pageviews), 0)          as pageviews
from analytics.api_sessions s
where s.site_id = :'site' and s.started_at >= :'from' and s.started_at < :'to'
group by 1, 2
order by visitors desc, key
limit 10;

\echo '=== 4. breakdown by page, 30 days (grouped from events: a visit reads many pages, D-098) ==='
explain (analyze, buffers, costs off, timing off, summary on)
select coalesce(e.pathname::text, '')                   as key,
       null::text                                       as label,
       count(distinct e.visitor_hash)                   as visitors,
       count(distinct e.session_id)                     as visits,
       count(*) filter (where e.name = 'pageview')      as pageviews
from analytics.api_events e
where e.site_id = :'site' and e.timestamp >= :'from' and e.timestamp < :'to'
group by 1, 2
order by visitors desc, key
limit 10;

\echo '=== 5. semi-join: visits filtered by a page, reached through their events (D-104) ==='
explain (analyze, buffers, costs off, timing off, summary on)
select count(distinct s.visitor_hash) as visitors, count(*) as visits
from analytics.api_sessions s
where s.site_id = :'site' and s.started_at >= :'from' and s.started_at < :'to'
  and exists (select 1 from analytics.api_events fe
              where fe.session_id = s.session_id and fe.site_id = s.site_id
                and fe.timestamp >= :'from' and fe.timestamp < :'to'
                and coalesce(fe.pathname::text, '') = '/pricing');

\echo '=== 6. semi-join the other way: events filtered by an entry page (D-104) ==='
explain (analyze, buffers, costs off, timing off, summary on)
select count(*) filter (where e.name = 'pageview') as pageviews
from analytics.api_events e
where e.site_id = :'site' and e.timestamp >= :'from' and e.timestamp < :'to'
  and exists (select 1 from analytics.api_sessions fs
              where fs.session_id = e.session_id and fs.site_id = e.site_id
                and fs.started_at >= :'from' and fs.started_at < :'to'
                and coalesce(fs.entry_page::text, '') = '/');

\echo '=== 7. goals, 30 days: two expressions per goal in one pass (D-125) ==='
explain (analyze, buffers, costs off, timing off, summary on)
with visitors as (select count(distinct s.visitor_hash) as total
                  from analytics.api_sessions s
                  where s.site_id = :'site' and s.started_at >= :'from' and s.started_at < :'to'),
     conversions as (select count(distinct case when e.name = 'Signup' then e.visitor_hash end) as v0,
                            count(*) filter (where e.name = 'Signup')                           as c0,
                            count(distinct case when (e.name = 'pageview' and e.pathname like '/blog/%' escape '\') then e.visitor_hash end) as v1,
                            count(*) filter (where (e.name = 'pageview' and e.pathname like '/blog/%' escape '\'))                           as c1
                     from analytics.api_events e
                     where e.site_id = :'site' and e.timestamp >= :'from' and e.timestamp < :'to')
select v.total, c.* from visitors v cross join conversions c;

\echo '=== 8. timeseries, 30 daily buckets in the site timezone (ADR 0020) ==='
explain (analyze, buffers, costs off, timing off, summary on)
with buckets as (select generate_series(:'from'::date, :'to'::date, interval '1 day')::date as bucket),
     visitors as (select (s.started_at at time zone 'Europe/Istanbul')::date as bucket,
                         count(distinct s.visitor_hash)                       as visitors
                  from analytics.api_sessions s
                  where s.site_id = :'site' and s.started_at >= :'from' and s.started_at < :'to'
                  group by 1),
     views as (select (hour at time zone 'Europe/Istanbul')::date as bucket, sum(pageviews) as pageviews
               from analytics.api_page_hourly
               where site_id = :'site' and hour >= :'from' and hour < :'to'
               group by 1)
select b.bucket, coalesce(v.visitors, 0), coalesce(w.pageviews, 0)
from buckets b
         left join visitors v on v.bucket = b.bucket
         left join views w on w.bucket = b.bucket
order by b.bucket;
