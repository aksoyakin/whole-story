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
     * A day is counted once and never recounted, so it has to be counted when it is actually finished — and
     * a day's last visit does not reach the database at midnight. A session that began at 23:58 is still
     * being extended after midnight, and a batch delayed by a restart or by consumer lag lands later still.
     * Counting at 00:00 would read a day that is still arriving and freeze the shortfall permanently, since
     * the insert does nothing on conflict.
     * <p>
     * The hourly rollups never needed this because they add to a running total and a late batch simply adds
     * to it. This one cannot: a distinct count is not a total you can add to, which is the whole reason the
     * table exists, and so the only defence is to look late rather than to look again.
     * <p>
     * An hour is far beyond the lag this pipeline shows when it is healthy — seconds — and the cost of being
     * generous is only that yesterday is answered from the sessions for one hour longer.
     */
    private static final Duration GRACE = Duration.ofHours(1);

    /**
     * Every session-backed dimension the dashboard breaks down by, plus the day's own totals.
     * <p>
     * One statement per day rather than one per dimension: there is no top-N to give up on the write side,
     * so grouping sets here cost a single pass over the day instead of eight (which is the opposite of what
     * the same shape did on the read side — see D-158).
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
                        else 'TOTAL' end,
                   case when grouping(s.referrer_source)  = 0 then coalesce(s.referrer_source, '')
                        when grouping(s.country_code)     = 0 then coalesce(s.country_code::text, '')
                        when grouping(s.subdivision_code) = 0 then coalesce(s.subdivision_code, '')
                        when grouping(s.city_geoname_id)  = 0 then coalesce(s.city_geoname_id::text, '')
                        when grouping(s.browser)          = 0 then coalesce(s.browser, '')
                        when grouping(s.os)               = 0 then coalesce(s.os, '')
                        when grouping(s.device_type)      = 0 then coalesce(s.device_type, '')
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
                (s.site_id, s.device_type), (s.site_id))
            on conflict (site_id, utc_day, dimension, value) do nothing
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
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT10M")
    public void catchUp() {
        LocalDate lastComplete = lastDayToCompute(clock.instant());
        LocalDate next = nextDayToCompute().orElse(null);
        if (next == null || next.isAfter(lastComplete)) {
            daysBehind.set(0);
            return;
        }

        daysBehind.set(lastComplete.toEpochDay() - next.toEpochDay() + 1);
        int done = 0;
        while (!next.isAfter(lastComplete) && done < MAX_DAYS_PER_RUN) {
            compute(next);
            next = next.plusDays(1);
            done++;
            daysBehind.decrementAndGet();
        }
        log.info("Rolled up {} day(s) of visitors, through {}; {} still behind", done, next.minusDays(1),
                daysBehind.get());
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
     * One day, then the watermark it moves. Deliberately two transactions rather than one, and safe because
     * of the order and not in spite of it: a crash between them leaves rows whose day the watermark does not
     * yet claim, and the next run simply computes that day again, which the {@code on conflict do nothing}
     * turns into a no-op. The reverse order would be the unsafe one — a watermark claiming a day whose rows
     * were never written is a day that is silently missing from every report over it.
     * <p>
     * It was annotated {@code @Transactional} at first, which did nothing at all: {@link #catchUp()} calls
     * this directly, so the call never leaves the object and never passes the proxy that would have begun
     * the transaction. The annotation is gone rather than worked around, because the ordering above is the
     * thing that actually provides the guarantee and a second mechanism claiming to provide it only hides
     * which one is load-bearing.
     */
    void compute(LocalDate day) {
        jdbc.sql(COMPUTE)
                .param("day", day)
                .param("dayStart", day.atStartOfDay().atOffset(ZoneOffset.UTC))
                .param("dayEnd", day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC))
                .update();
        jdbc.sql("update analytics.visitor_daily_watermark set covered_through = :day, updated_at = now()")
                .param("day", day)
                .update();
    }
}
