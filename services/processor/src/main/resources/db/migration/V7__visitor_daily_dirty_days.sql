-- Days whose rollup is known to be out of date, so that a day counted once can still be counted again.
--
-- The hole this closes, which V4 named and left open (D-164, Q-21): a day is computed an hour after it ends
-- and never recomputed, because the insert does nothing on conflict and the watermark only moves forwards.
-- That hour absorbs the ordinary case -- a visit that began at 23:58 is still being extended after midnight,
-- and the session timeout caps that at 00:30 -- but it cannot absorb an outage longer than itself. Sessions
-- arriving for a day already counted were simply dropped on the floor, and every report over a range
-- containing that day was short by them, with nothing saying so: the rollup was not behind, so
-- visitor_daily.days_behind read zero.
--
-- It mattered more after each round that added a dimension. Ten of the dashboard's cards, the summary tile
-- and the goals' denominator are now answered from this table (D-160, D-169, D-173), so "the rollup is an
-- accelerator and never an authority" needed re-examining. That principle still holds, and it is worth being
-- precise about what it covers: a day the watermark has *not* reached is counted live and is therefore
-- correct. A day the watermark has *wrongly* reached was protected by nothing at all. This is that day.
--
-- How it works: the writer marks a day whenever it stores a batch belonging to one the watermark already
-- claims, in the same transaction as the rows themselves, so the mark cannot survive without the data or the
-- data without the mark. The rollup job then recomputes marked days and the two compute statements become
-- `on conflict do update`. In normal operation nothing is ever inserted here, because the day a batch
-- belongs to is today and today is always beyond the watermark.
--
-- Marked per day rather than per site, which is a deliberate trade. A site filter would mean a second
-- variant of both compute statements -- a third definition of the rollup, and the one nobody updates, which
-- is the reason the backfill script extracts its SQL from the service's own source rather than copying it. A
-- day's recompute is the same work the job does for every day anyway, and marking is rare by construction.

create table analytics.visitor_daily_dirty (
    utc_day date   primary key,
    -- Which mark this is, rather than when it was made.
    --
    -- The job reads a day, recomputes it, then deletes the row -- and has to not delete a mark made by a
    -- batch that landed while it was recomputing, because that batch's rows may be outside its snapshot. So
    -- the delete matches on this value, and a re-mark changes it.
    --
    -- A timestamp would have served, and would have left an argument about resolution to make: two marks
    -- sharing a microsecond would lose one. A sequence removes the argument instead of winning it -- the
    -- values are distinct by construction, and the comparison is exact equality rather than an ordering.
    marked  bigint not null
);

create sequence analytics.visitor_daily_dirty_seq owned by analytics.visitor_daily_dirty.marked;

-- Nothing is granted to wholestory_api and no api_* view is added: this is the processor's own plumbing,
-- not part of the read contract (D-012). What api needs to know about the rollup it already learns from the
-- watermark view, and a report's correctness does not depend on reading this -- a marked day is answered
-- from the rollup exactly as before until the job recomputes it. That is the one thing this table does not
-- fix, and it is a bounded wrongness rather than an unbounded one: at most the interval between runs.
