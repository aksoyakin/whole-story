-- A day's visitors, counted once per day and summed across days.
--
-- ADR 0007 says unique visitors are not additive and no rollup can hold them. That is true of hours and
-- false of days, and the reason is ADR 0005: the hashing salt rotates every UTC day, so the same person is a
-- different visitor_hash tomorrow and two days hold disjoint sets of hashes. A distinct count over a range
-- is therefore the exact sum of its days' — measured, 54,176 against 54,176, and per country across 193 of
-- them the difference was zero (docs/performance.md, D-159).
--
-- The grain is the UTC day because that is the salt's grain, and nothing else works: an hourly rollup would
-- count a visitor once per hour they were active, and one keyed on the site's own local day overcounted by
-- 16%, since one UTC day straddles two local ones.
--
-- A site's day does not line up with this (ADR 0020), so a reader takes the whole interior days from here
-- and counts the two partial days at the edges live. That reproduced the live figure exactly.

create table analytics.visitor_daily (
    site_id      uuid   not null,
    utc_day      date   not null,
    -- The Dimension this row groups by, or 'TOTAL' for the day's figures with no grouping at all. A total is
    -- stored rather than summed out of another dimension's rows: a visitor who changed country mid-day would
    -- appear under two of them, and the sum would then exceed the day's real count.
    dimension    text   not null,
    -- Empty is a value, not a gap: it is the row a breakdown shows for visits where the dimension is unknown.
    value        text   not null,
    -- What a person reads where the value is an id or a code: a city is a geoname id and a region an ISO
    -- code. Carried here because a breakdown answered from this table still has to label its rows, and
    -- outside the key because it is a property of the value rather than part of it.
    label        text,
    visitors     bigint not null,
    visits       bigint not null,
    -- A breakdown reports these next to the visitors, and both are plainly additive.
    pageviews    bigint not null,
    -- Stored, even though the bounce definition deliberately lives in the api_sessions view (D-077), because
    -- counting them live would mean scanning the sessions the rest of this table exists to avoid. The cost is
    -- that redefining a bounce is no longer one `create or replace view`: this table has to be rebuilt too.
    bounces      bigint not null,
    duration_sum bigint not null,
    primary key (site_id, utc_day, dimension, value)
);

create index visitor_daily_lookup_idx on analytics.visitor_daily (site_id, dimension, utc_day);

-- How far the rollup is known to be complete. One row, and a watermark rather than a set of finished days:
-- a day with no traffic produces no rows, so "no rows" cannot tell a quiet day from a day nobody computed,
-- and arbitrary gaps would split the live remainder into more intervals than a query can reasonably carry.
-- The job fills oldest first, which keeps this contiguous by construction.
create table analytics.visitor_daily_watermark (
    only_row        boolean     primary key default true check (only_row),
    covered_through date,
    updated_at      timestamptz not null,
    constraint visitor_daily_watermark_one_row check (only_row)
);

insert into analytics.visitor_daily_watermark (only_row, covered_through, updated_at)
values (true, null, now());

-- The read contract with api (D-012). An absent watermark means nothing is covered, which is the state a
-- fresh database starts in and the state the reader has to treat as "compute everything live".
create view analytics.api_visitor_daily as
select site_id, utc_day, dimension, value, label, visitors, visits, pageviews, bounces, duration_sum
from analytics.visitor_daily;

create view analytics.api_visitor_daily_watermark as
select covered_through
from analytics.visitor_daily_watermark;

grant select on analytics.api_visitor_daily, analytics.api_visitor_daily_watermark to wholestory_api;
