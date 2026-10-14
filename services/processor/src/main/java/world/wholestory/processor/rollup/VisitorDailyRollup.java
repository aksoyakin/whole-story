package world.wholestory.processor.rollup;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts each finished UTC day's visitors once, so that a report over a range can add days up instead of
 * reaching for every session in it (D-159, ADR 0007's amendment).
 * <p>
 * Only whole days are computed. Today is still moving, so it is left to the reader to count live — which is
 * also what makes this safe to be behind: anything this table does not cover is simply counted the way it
 * always was. The rollup is an accelerator and never an authority, so a day it misses costs speed, never
 * correctness.
 * <p>
 * Runs on a short interval rather than once a night. The same loop then does three jobs with one rule — it
 * picks up yesterday when yesterday ends, it backfills history the first time it ever runs, and it heals a
 * gap left by a restart — and a gap heals in minutes instead of waiting a day. Each run is capped so a
 * backfill of two years cannot hold the thread or the transaction for as long as it would take in one go.
 */
@Slf4j
@Component
public class VisitorDailyRollup {

    /** Enough that a two-year backfill finishes in a few runs, small enough that one run stays short. */
    private static final int MAX_DAYS_PER_RUN = 30;

    /**
     * How long after a day ends before it is counted.
     * <p>
     * A day's last visit does not reach the database at midnight. A session that began at 23:58 is still
     * being extended after midnight — the session timeout caps that at 00:30 — and a batch delayed by a
     * restart or by consumer lag lands later still. Counting at 00:00 would read a day that is still
     * arriving.
     * <p>
     * The hourly rollups never needed this because they add to a running total and a late batch simply adds
     * to it. This one cannot: a distinct count is not a total you can add to, which is the whole reason the
     * table exists, and so the defence is to look late.
     * <p>
     * An hour is far beyond the lag this pipeline shows when it is healthy — seconds — and the cost of being
     * generous is only that yesterday is answered from the sessions for one hour longer.
     * <p>
     * It used to be the *only* defence, and that was the hole: an outage longer than this window meant
     * sessions arriving for a day already counted, dropped silently because the insert did nothing on
     * conflict and the watermark never went back (D-164). What this window does now is keep the common case
     * to a single computation — without it, the extensions that land between midnight and 00:30 would mark
     * every single day for recomputation. {@link #recomputeDirtyDays()} covers what it cannot bound, so
     * being generous here is cheap and being wrong here is no longer permanent.
     */
    private static final Duration GRACE = Duration.ofHours(1);

    /**
     * Every session-backed dimension the dashboard breaks down by, plus the day's own totals.
     * <p>
     * One statement per day rather than one per dimension: there is no top-N to give up on the write side,
     * so grouping sets here cost a single pass over the day instead of nine (which is the opposite of what
     * the same shape did on the read side — see D-158).
     * <p>
     * The entry and exit pages are the newest two, and that they are grouping sets rather than a statement
     * of their own is the whole argument for adding them: a visit carries both as columns, so the pass this
     * already makes over the day answers them. Cheap is not free, and the scan is the part that is shared —
     * the rows still have to be written, and they roughly double what a day stores. Measured, a full 712-day
     * backfill went from 156 s to 236 s.
     */
    private static final String COMPUTE = """
            insert into analytics.visitor_daily
                (site_id, utc_day, dimension, value, label, visitors, visits, pageviews, bounces, duration_sum)
            select s.site_id,
                   :day,
                   case when grouping(s.referrer_source)  = 0 then 'SOURCE'
                        when grouping(s.country_code)     = 0 then 'COUNTRY'
                        when grouping(s.subdivision_code) = 0 then 'REGION'
                        when grouping(s.city_geoname_id)  = 0 then 'CITY'
                        when grouping(s.browser)          = 0 then 'BROWSER'
                        when grouping(s.os)               = 0 then 'OS'
                        when grouping(s.device_type)      = 0 then 'DEVICE'
                        when grouping(s.entry_page)       = 0 then 'ENTRY_PAGE'
                        when grouping(s.exit_page)        = 0 then 'EXIT_PAGE'
                        else 'TOTAL' end,
                   case when grouping(s.referrer_source)  = 0 then coalesce(s.referrer_source, '')
                        when grouping(s.country_code)     = 0 then coalesce(s.country_code::text, '')
                        when grouping(s.subdivision_code) = 0 then coalesce(s.subdivision_code, '')
                        when grouping(s.city_geoname_id)  = 0 then coalesce(s.city_geoname_id::text, '')
                        when grouping(s.browser)          = 0 then coalesce(s.browser, '')
                        when grouping(s.os)               = 0 then coalesce(s.os, '')
                        when grouping(s.device_type)      = 0 then coalesce(s.device_type, '')
                        when grouping(s.entry_page)       = 0 then coalesce(s.entry_page, '')
                        when grouping(s.exit_page)        = 0 then coalesce(s.exit_page, '')
                        else '' end,
                   -- A label only exists where the value is a code or an id; max() because it is determined
                   -- by the value and this avoids widening the key with something nobody groups by.
                   case when grouping(s.subdivision_code) = 0 then max(s.subdivision_name)
                        when grouping(s.city_geoname_id)  = 0 then max(s.city_name)
                        else null end,
                   count(distinct s.visitor_hash),
                   count(*),
                   coalesce(sum(s.pageviews), 0),
                   count(*) filter (where s.is_bounce),
                   coalesce(sum(s.duration_seconds), 0)
            from analytics.api_sessions s
            where s.started_at >= :dayStart and s.started_at < :dayEnd
            group by grouping sets (
                (s.site_id, s.referrer_source), (s.site_id, s.country_code), (s.site_id, s.subdivision_code),
                (s.site_id, s.city_geoname_id), (s.site_id, s.browser), (s.site_id, s.os),
                (s.site_id, s.device_type), (s.site_id, s.entry_page), (s.site_id, s.exit_page),
                (s.site_id))
            on conflict (site_id, utc_day, dimension, value) do update set
                label        = excluded.label,
                visitors     = excluded.visitors,
                visits       = excluded.visits,
                pageviews    = excluded.pageviews,
                bounces      = excluded.bounces,
                duration_sum = excluded.duration_sum
            """;

    /**
     * The page dimension, which is the one the dashboard asks for most and the one that cost the most.
     * <p>
     * A separate statement because it is the only dimension that is not a property of a visit: a page belongs
     * to a pageview, so it is grouped from the events rather than from the sessions, and no grouping set over
     * the sessions could have reached it.
     * <p>
     * Visitors is the only figure stored. The other columns are left null rather than filled with something
     * defensible, because nothing reads them for this dimension and a page has no bounce rate or visit
     * duration to report — and because {@code count(distinct session_id)}, the obvious thing to put in
     * {@code visits}, is the exact work this rollup exists to stop doing.
     */
    private static final String COMPUTE_PAGES = """
            insert into analytics.visitor_daily
                (site_id, utc_day, dimension, value, label, visitors)
            select e.site_id, :day, 'PAGE', coalesce(e.pathname, ''), null, count(distinct e.visitor_hash)
            from analytics.api_events e
            where e.timestamp >= :dayStart and e.timestamp < :dayEnd
            group by e.site_id, coalesce(e.pathname, '')
            -- Only the one figure this dimension stores. The other four stay null because a page row never
            -- had them, and the label because a path is its own name.
            on conflict (site_id, utc_day, dimension, value) do update set visitors = excluded.visitors
            """;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final AtomicLong daysBehind = new AtomicLong();

    VisitorDailyRollup(JdbcClient jdbc, Clock clock, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.clock = clock;
        // A rollup that silently stops being filled makes every report slower and nothing says why, which is
        // the failure this project keeps meeting (D-059, D-144). Reported as a number rather than as health:
        // being behind is not a reason to restart a container that is answering correctly.
        Gauge.builder("analytics.visitor_daily.days_behind", daysBehind::get)
                .description("Finished UTC days not yet rolled up; reports fall back to counting them live")
                .register(meters);
        // The second half of that question, and the one D-164 recorded as having no answer: a day the
        // watermark already claims but whose sessions have moved since. The gauge above reads zero for it,
        // because the rollup is not behind — it is wrong, which is worse and was invisible.
        //
        // Read from the table at scrape time rather than from a counter in this object, and not for the
        // reason the outbox gauge is: here it is necessary. The marks are written by the consumer's thread
        // and cleared by this one, so a field on this class would only ever see half of what happened.
        Gauge.builder("analytics.visitor_daily.dirty_days", this, VisitorDailyRollup::countDirtyDays)
                .description("Counted UTC days whose sessions have changed since; awaiting recomputation")
                .register(meters);
    }

    /** NaN rather than zero when the query fails: "nothing to redo" and "cannot tell" are different answers. */
    private double countDirtyDays() {
        try {
            Long count = jdbc.sql("select count(*) from analytics.visitor_daily_dirty")
                    .query(Long.class)
                    .single();
            return count == null ? Double.NaN : count;
        } catch (RuntimeException e) {
            log.debug("Could not read the dirty day count", e);
            return Double.NaN;
        }
    }

    /**
     * Two jobs: move forward over the days nobody has counted, then count again the days somebody has
     * changed since. The second has to run even when the first has nothing to do, which is why it is not
     * inside the early return below.
     */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT10M")
    public void catchUp() {
        advance();
        recomputeDirtyDays();
    }

    private void advance() {
        LocalDate lastComplete = lastDayToCompute(clock.instant());
        LocalDate next = nextDayToCompute().orElse(null);
        if (next == null || next.isAfter(lastComplete)) {
            daysBehind.set(0);
            return;
        }

        daysBehind.set(lastComplete.toEpochDay() - next.toEpochDay() + 1);
        int done = 0;
        while (!next.isAfter(lastComplete) && done < MAX_DAYS_PER_RUN) {
            computeRows(next);
            markCovered(next);
            next = next.plusDays(1);
            done++;
            daysBehind.decrementAndGet();
        }
        log.info("Rolled up {} day(s) of visitors, through {}; {} still behind", done, next.minusDays(1),
                daysBehind.get());
    }

    /**
     * Counts again the days a batch landed for after they had already been counted (Q-21, D-164).
     * <p>
     * Oldest first and capped, like the forward pass: an outage of a week leaves a week of marks, and one run
     * should not hold the thread for as long as it takes to redo all of them.
     * <p>
     * Recompute, then clear the mark — never the other way round. A crash in between leaves a day marked
     * whose rows are already correct, and the next run redoes work; the reverse leaves a day whose mark is
     * gone and whose rows are not, which is the silent shortfall this whole mechanism exists to remove. The
     * same ordering argument as the watermark's, for the same reason.
     * <p>
     * The clear matches on the mark it read, so a batch that landed while this was recomputing is not thrown
     * away: its mark is a different value, the delete does nothing, and the day is recomputed again next run.
     * That is also why the recompute is not wrapped in one transaction with the clear — it would not help,
     * since the batch commits independently either way, and it would hold a write lock for the length of a
     * full day's aggregation.
     */
    private void recomputeDirtyDays() {
        List<DirtyDay> dirty = jdbc.sql("""
                        select utc_day, marked from analytics.visitor_daily_dirty
                        order by utc_day limit :limit""")
                .param("limit", MAX_DAYS_PER_RUN)
                .query((rs, row) -> new DirtyDay(rs.getObject("utc_day", LocalDate.class), rs.getLong("marked")))
                .list();
        if (dirty.isEmpty()) {
            return;
        }
        int recomputed = 0;
        for (DirtyDay day : dirty) {
            computeRows(day.day());
            int cleared = jdbc.sql("""
                            delete from analytics.visitor_daily_dirty
                            where utc_day = :day and marked = :marked""")
                    .param("day", day.day())
                    .param("marked", day.marked())
                    .update();
            if (cleared == 0) {
                log.info("{} was marked again while it was being recomputed; leaving it for the next run",
                        day.day());
            }
            recomputed++;
        }
        log.info("Recomputed {} day(s) whose rollup had fallen behind the sessions", recomputed);
    }

    /** A day awaiting recomputation, and which mark asked for it. */
    private record DirtyDay(LocalDate day, long marked) {
    }

    /**
     * The newest day that is finished and settled: the last one that ended at least {@link #GRACE} ago.
     * Pure and package-visible so the boundary can be tested at an instant rather than at whatever time of
     * day the suite happens to run.
     */
    static LocalDate lastDayToCompute(Instant now) {
        return now.minus(GRACE).atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1);
    }

    /**
     * Where to resume: the day after the watermark, or the first day there is any session at all. A database
     * with no sessions yet has nothing to compute and no watermark to move.
     */
    private Optional<LocalDate> nextDayToCompute() {
        LocalDate covered = jdbc.sql("select covered_through from analytics.visitor_daily_watermark")
                .query(LocalDate.class)
                .optional()
                .orElse(null);
        if (covered != null) {
            return Optional.of(covered.plusDays(1));
        }
        return jdbc.sql("select min(started_at at time zone 'UTC')::date from analytics.sessions")
                .query(LocalDate.class)
                .optional();
    }

    /**
     * One day's rows — both statements, since a day is covered as a whole and not one dimension at a time.
     * <p>
     * Writing the rows and moving the watermark are separate on purpose, and safe because of the order
     * rather than in spite of it: a crash between them leaves rows whose day the watermark does not yet
     * claim, and the next run computes that day again, which the upsert makes harmless. The reverse order
     * would be the unsafe one — a watermark claiming a day whose rows were never written is a day silently
     * missing from every report over it.
     * <p>
     * It is also why this is a method of its own rather than part of the step that advances the watermark: a
     * day being recomputed because it fell behind must not drag the watermark back to itself. The watermark
     * is a statement about how far the table reaches, not about which day was touched last.
     * <p>
     * {@code compute} was annotated {@code @Transactional} at first, which did nothing at all:
     * {@link #catchUp()} calls it directly, so the call never leaves the object and never passes the proxy
     * that would have begun the transaction. The annotation is gone rather than worked around, because the
     * ordering above is the thing that actually provides the guarantee and a second mechanism claiming to
     * provide it only hides which one is load-bearing.
     */
    void computeRows(LocalDate day) {
        for (String statement : new String[] {COMPUTE, COMPUTE_PAGES}) {
            jdbc.sql(statement)
                    .param("day", day)
                    .param("dayStart", day.atStartOfDay().atOffset(ZoneOffset.UTC))
                    .param("dayEnd", day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                    .update();
        }
    }

    private void markCovered(LocalDate day) {
        jdbc.sql("update analytics.visitor_daily_watermark set covered_through = :day, updated_at = now()")
                .param("day", day)
                .update();
    }
}
