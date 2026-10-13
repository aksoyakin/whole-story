-- Times the dashboard's queries by running each of them repeatedly and reporting the median
-- (docs/performance.md). Run with read-path-timings.sh.
--
-- Separate from read-path-queries.sql, which prints plans. That one explains *why* a query costs what it
-- costs; this one establishes *what* it costs, and the two need different methods. A single EXPLAIN ANALYZE
-- is not a measurement: run back to back on this corpus the same statement came out at 2216, 3521 and
-- 2348 ms, so the spread between runs was larger than the effect being looked for. Each statement is
-- therefore run :reps times and the median is what gets reported, with min and max alongside so the spread
-- stays visible instead of being averaged away.
--
-- Postgres is left at production's settings on purpose (shared_buffers 128 MB, work_mem 4 MB): the point is
-- what the deployment that exists does, not what a tuned one could do. The work_mem comparison is a separate
-- run of this same file with the setting changed, so the two differ in one thing only.

\set ON_ERROR_STOP on

create temp table q (n int primary key, label text, sql text);

insert into q values
(1, 'summary, no filter', $q$
select v.visitors, v.visits, v.bounce_rate, v.avg_duration, p.pageviews
from (select count(distinct s.visitor_hash) as visitors, count(*) as visits,
             coalesce(avg(s.is_bounce::int), 0) as bounce_rate,
             coalesce(avg(s.duration_seconds), 0) as avg_duration
      from analytics.api_sessions s
      where s.site_id = $1 and s.started_at >= $2 and s.started_at < $3) v
cross join (select coalesce(sum(pageviews), 0) as pageviews from analytics.api_page_hourly
            where site_id = $1 and hour >= $2 and hour < $3) p $q$),

(2, 'summary, filtered (D-103)', $q$
select v.visitors, v.visits, v.bounce_rate, v.avg_duration, p.pageviews
from (select count(distinct s.visitor_hash) as visitors, count(*) as visits,
             coalesce(avg(s.is_bounce::int), 0) as bounce_rate,
             coalesce(avg(s.duration_seconds), 0) as avg_duration
      from analytics.api_sessions s
      where s.site_id = $1 and s.started_at >= $2 and s.started_at < $3
        and coalesce(s.country_code::text, '') = 'TR') v
cross join (select count(*) filter (where e.name = 'pageview') as pageviews from analytics.api_events e
            where e.site_id = $1 and e.timestamp >= $2 and e.timestamp < $3
              and coalesce(e.country_code::text, '') = 'TR') p $q$),

(3, 'breakdown: source (sessions)', $q$
select coalesce(s.referrer_source::text, '') as key, count(distinct s.visitor_hash) as visitors,
       count(*) as visits, coalesce(sum(s.pageviews), 0) as pageviews
from analytics.api_sessions s
where s.site_id = $1 and s.started_at >= $2 and s.started_at < $3
group by 1 order by visitors desc, key limit 10 $q$),

(4, 'breakdown: page (events, D-098)', $q$
select coalesce(e.pathname::text, '') as key, count(distinct e.visitor_hash) as visitors,
       count(distinct e.session_id) as visits, count(*) filter (where e.name = 'pageview') as pageviews
from analytics.api_events e
where e.site_id = $1 and e.timestamp >= $2 and e.timestamp < $3
group by 1 order by visitors desc, key limit 10 $q$),

(5, 'semi-join: visits by page (D-104)', $q$
select count(distinct s.visitor_hash) as visitors, count(*) as visits
from analytics.api_sessions s
where s.site_id = $1 and s.started_at >= $2 and s.started_at < $3
  and exists (select 1 from analytics.api_events fe
              where fe.session_id = s.session_id and fe.site_id = s.site_id
                and fe.timestamp >= $2 and fe.timestamp < $3
                and coalesce(fe.pathname::text, '') = '/pricing') $q$),

(6, 'semi-join: events by entry page (D-104)', $q$
select count(*) filter (where e.name = 'pageview') as pageviews
from analytics.api_events e
where e.site_id = $1 and e.timestamp >= $2 and e.timestamp < $3
  and exists (select 1 from analytics.api_sessions fs
              where fs.session_id = e.session_id and fs.site_id = e.site_id
                and fs.started_at >= $2 and fs.started_at < $3
                and coalesce(fs.entry_page::text, '') = '/') $q$),

(7, 'goals, two goals one pass (D-125)', $q$
with visitors as (select count(distinct s.visitor_hash) as total from analytics.api_sessions s
                  where s.site_id = $1 and s.started_at >= $2 and s.started_at < $3),
     conversions as (select count(distinct case when e.name = 'Signup' then e.visitor_hash end) as v0,
                            count(*) filter (where e.name = 'Signup') as c0,
                            count(distinct case when (e.name = 'pageview' and e.pathname like '/blog/%') then e.visitor_hash end) as v1,
                            count(*) filter (where (e.name = 'pageview' and e.pathname like '/blog/%')) as c1
                     from analytics.api_events e
                     where e.site_id = $1 and e.timestamp >= $2 and e.timestamp < $3)
select v.total, c.* from visitors v cross join conversions c $q$),

(8, 'timeseries, daily buckets (ADR 0020)', $q$
with buckets as (select generate_series($2::date, $3::date, interval '1 day')::date as bucket),
     visitors as (select (s.started_at at time zone 'Europe/Istanbul')::date as bucket,
                         count(distinct s.visitor_hash) as visitors
                  from analytics.api_sessions s
                  where s.site_id = $1 and s.started_at >= $2 and s.started_at < $3 group by 1),
     views as (select (hour at time zone 'Europe/Istanbul')::date as bucket, sum(pageviews) as pageviews
               from analytics.api_page_hourly
               where site_id = $1 and hour >= $2 and hour < $3 group by 1)
select b.bucket, coalesce(v.visitors, 0), coalesce(w.pageviews, 0)
from buckets b left join visitors v on v.bucket = b.bucket left join views w on w.bucket = b.bucket
order by b.bucket $q$);

create temp table timing (n int, pass int, ms numeric);

-- Carried in a table rather than read straight from psql variables: inside a dollar-quoted DO body psql does
-- no substitution at all, so :'site' would have reached the server verbatim.
create temp table params as
select :'site'::uuid as site, :'from'::timestamptz as from_t, :'to'::timestamptz as to_t, :reps::int as reps;

do $$
declare
    r      record;
    i      int;
    t0     timestamptz;
    p      record;
begin
    select * into p from params;
    for r in select * from q order by n loop
        for i in 1 .. p.reps loop
            t0 := clock_timestamp();
            -- Every output column is forced by casting the whole row to text, and that detail is the
            -- difference between a measurement and a zero. Wrapping the statement in count(*) instead --
            -- which is the obvious way to run a query and throw the rows away -- let the planner notice that
            -- nothing outside referenced the aggregates, and since an aggregate with no GROUP BY returns one
            -- row whatever the data, it skipped computing them altogether. Four of the eight statements
            -- reported 0 ms that way, including ones measured at 400 and 1500 ms a moment earlier.
            execute 'select sum(length(d::text)) from (' || r.sql || ') d'
                using p.site, p.from_t, p.to_t;
            insert into timing values (r.n, i, extract(epoch from clock_timestamp() - t0) * 1000);
        end loop;
        raise notice '% done', r.label;
    end loop;
end $$;

select q.n,
       q.label,
       count(*)                                                                as reps,
       round(min(t.ms))                                                        as min_ms,
       round(percentile_cont(0.5) within group (order by t.ms))                as median_ms,
       round(max(t.ms))                                                        as max_ms,
       round(100 * (max(t.ms) - min(t.ms))
             / nullif(percentile_cont(0.5) within group (order by t.ms), 0))    as spread_pct
from timing t join q on q.n = t.n
group by q.n, q.label
order by median_ms desc;

-- The whole page, which is the only figure a visitor experiences: a dashboard render issues a summary, a
-- timeseries, the goals, the realtime count and eight breakdowns. Summed from the medians above rather than
-- measured as one request, because the web app issues them concurrently and what this says is how much
-- database work one render asks for.
with med as (select t.n, percentile_cont(0.5) within group (order by t.ms) as ms from timing t group by t.n)
select round((select ms from med where n = 1)        -- the summary
           + (select ms from med where n = 8)        -- the timeseries
           + (select ms from med where n = 7)        -- the goals
           + (select ms from med where n = 4)        -- the page breakdown, grouped from events
           + 7 * (select ms from med where n = 3))   -- the seven breakdowns that read sessions
       as one_render_of_database_work_ms;
