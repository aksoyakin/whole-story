-- The page joins the daily rollup, and it is the first dimension in this table that is not a property of a
-- visit.
--
-- Why it belongs here at all, when V4 deliberately left the high cardinality dimensions out: that reasoning
-- was about adding a *new* axis. The pathname axis already exists in this schema, in page_hourly, at a grain
-- twenty-four times finer. Measured on the seeded corpus of 49.4M events: page_hourly holds 11,143,450 rows
-- and the same pages at a daily grain are 1,040,394 — a tenth of a rollup we already keep. An entry page or
-- a campaign would still be a second copy of the sessions; a page is not.
--
-- Why it is worth it: over twelve months the page breakdown was the single most expensive thing the
-- dashboard asked for, and the scan was only a sixth of it. The rest was counting distinct visitors per
-- path, which is exactly what a day at the salt's grain can hold (D-159).
--
-- The columns below become nullable because they are answers to questions a page row cannot be asked. Visits,
-- bounces and the duration sum belong to a visit, and a page is not one; they stay not-null in spirit for the
-- rows that have them, and the reader only ever reads them from the 'TOTAL' row. Storing a zero instead would
-- be worse than storing nothing: zero is a number, and a number that is not true is the kind of thing nobody
-- notices.
alter table analytics.visitor_daily
    alter column visits drop not null,
    alter column pageviews drop not null,
    alter column bounces drop not null,
    alter column duration_sum drop not null;

-- And the watermark goes back to the beginning, which is the whole reason this migration is not just an
-- ALTER.
--
-- The watermark is one row for the table, not one per dimension. Every day up to it is already claimed as
-- covered, and the job only ever moves forwards, so without this the days that exist would never be given
-- their page rows -- while the reader, which consults that same watermark, would take those days from the
-- rollup and find nothing there. A twelve-month page breakdown would come back holding only the two partial
-- days at the edges of the range and looking completely ordinary.
--
-- That is the same failure D-161 found during the last rollup, arriving by a different route: there the
-- reader asked for a dimension the writer never computed, here it asks for days the writer skipped. Both are
-- silent, and both return a plausible number.
--
-- Rewinding is safe because every insert is `on conflict do nothing`: the days that were computed are
-- recomputed into no-ops, the page rows are new, and the job paces itself at thirty days a run. Until it
-- catches up the answers are the same and only slower, which is what it means for the rollup to be an
-- accelerator and not an authority.
update analytics.visitor_daily_watermark set covered_through = null, updated_at = now();
