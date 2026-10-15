-- How far back raw events and sessions still reach, published for api to read.
--
-- ADR 0007 decided raw data is kept 24 months and rollups indefinitely. The keeping half was built — a job
-- creates monthly partitions ahead of time — and the dropping half never was. Building it turned out not to
-- be a loop over old months, because it changes what api can honestly answer.
--
-- A report takes the interior UTC days of a range from visitor_daily and counts the two partial days at its
-- edges live (D-160), since a site's day is cut in its own timezone and never on a UTC midnight. Once the
-- raw data under those edges is gone, the edges count zero: a range starting just before the horizon comes
-- back short by about a day and says nothing, a filtered one comes back empty (a filter cannot be answered
-- from the rollup at all, D-103), and the summary's pageviews stay where they were because page_hourly is
-- kept forever. The page would contradict itself while every number in it looked plausible.
--
-- So the horizon is published rather than left inside the service that enforces it: partition maintenance
-- records the first day raw data still covers, and api refuses a range that begins before it. Refusing is
-- the same choice as D-171's ceiling on how long a range may be — somebody asked for a specific window, and
-- answering a different one without saying so is the silently wrong number this schema keeps finding the
-- expensive way.
--
-- Two properties are deliberate. It reports what the catalogue actually holds rather than what any
-- configuration claims, so a retention that never ran cannot make api promise history that is gone, and a
-- shorter retention cannot make it answer wrongly — only over a shorter window. And it starts null, which
-- api reads as "no floor": the behaviour it had before this table existed, until the job has run once.

create table analytics.raw_data_retention (
    only_row      boolean primary key default true check (only_row),
    retained_from date,
    updated_at    timestamptz not null
);

insert into analytics.raw_data_retention (only_row, retained_from, updated_at)
values (true, null, now());

-- The read contract with api (D-012), as narrow as the watermark's: one date, nothing about partitions.
create view analytics.api_raw_data_retention as
select retained_from
from analytics.raw_data_retention;

grant select on analytics.api_raw_data_retention to wholestory_api;
