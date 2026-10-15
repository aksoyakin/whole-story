package world.wholestory.processor.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Both ends of the monthly partitions of events and sessions (D-033).
 * <p>
 * Forward: there is no DEFAULT partition, so an insert into a missing month fails — partitions for the
 * previous, current and next two months always exist. Runs once on startup (after Flyway, before the Kafka
 * listeners start) and daily afterwards.
 * <p>
 * Backward: raw data is kept {@link #RETENTION_MONTHS} months and the rollups indefinitely, which is what
 * ADR 0007 decided and what nothing implemented until now. What the two ends have in common is that neither
 * is allowed to be a surprise, so the job also records how far back the raw data now reaches, for api to
 * refuse ranges that begin before it ({@code V8__raw_data_retention.sql}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PartitionMaintenance implements InitializingBean {

    private static final String[] PARTITIONED_TABLES = {"events", "sessions"};
    private static final int MONTHS_AHEAD = 2;

    /**
     * How long raw events and sessions are kept, from ADR 0007.
     * <p>
     * A constant rather than a property, for the same reason {@link #MONTHS_AHEAD} is one: this is a design
     * decision whose reasoning lives next to it, not an operational dial, and a setting that exists only in
     * the production compose file is a setting that has never run (D-147).
     * <p>
     * Monthly partitions mean the real figure lands between 24 and 25 months — a month is dropped when all of
     * it is older than the horizon, never when part of it is. Worth stating rather than rounding away: it is
     * the difference between the promise and the implementation, and the implementation is the generous one.
     * <p>
     * It is far longer than the 366 days one report may cover, so the longest range the dashboard offers
     * always fits inside the retained window. That relationship is not load-bearing, though, and the reason
     * is worth naming: what protects a report is api's floor, which comes from the data rather than from this
     * number. Shortening this would shorten how far back reports reach and could not make one wrong.
     */
    static final int RETENTION_MONTHS = 24;

    /**
     * Interpolated into a {@code drop table}, so it is checked rather than trusted. The name comes from the
     * catalogue and cannot be anything else today; the check costs nothing and the statement is irreversible.
     */
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z0-9_]+$");

    /**
     * Every partition attached to the two tables, with the instants its range actually covers.
     * <p>
     * The catalogue decides what may be dropped, not a generated list of names. A loop over names it builds
     * itself can only ever find what it already assumed: it would silently keep a month attached under a
     * different name forever, and it would aim a {@code drop table} at whatever happened to answer to the
     * name it expected. This project has had DDL applied to it by hand (D-145), so "the partition called
     * events_2024_01 covers January 2024" is an assumption rather than a fact.
     * <p>
     * The bounds are extracted in SQL and cast back to {@code timestamptz} rather than compared as text. The
     * literal {@code pg_get_expr} prints is rendered in the session's timezone, so a text comparison would
     * depend on where the JVM happens to think it is; the literal carries its own offset, so casting it
     * recovers the exact instant wherever it runs. Anything whose bounds are not a plain range — a list
     * partition, a DEFAULT, MINVALUE/MAXVALUE — fails the match and arrives here as null, which is read
     * below as "do not touch this".
     */
    private static final String PARTITIONS = """
            select c.relname               as name,
                   (b.bounds)[1]::timestamptz as lower_bound,
                   (b.bounds)[2]::timestamptz as upper_bound
            from pg_class c
                     join pg_inherits i on i.inhrelid = c.oid
                     join pg_class p on p.oid = i.inhparent
                     join pg_namespace n on n.oid = p.relnamespace
                     cross join lateral (select regexp_match(
                                                    pg_get_expr(c.relpartbound, c.oid),
                                                    '^FOR VALUES FROM \\(''(.+)''\\) TO \\(''(.+)''\\)$') as bounds) b
            where n.nspname = 'analytics' and p.relname in (:tables)
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    @Override
    public void afterPropertiesSet() {
        maintain();
    }

    /**
     * Forward first, then backward, then publish where backward got to. The order is the only one that is
     * safe to be interrupted at: a crash after creating months leaves a database that can still accept
     * writes, and a crash after dropping leaves a published horizon that is older than the truth, which
     * makes api offer a range whose edges are empty — so the horizon is written last and from what survived.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    void maintain() {
        maintain(clock.instant());
    }

    /**
     * Package-visible with the instant as a parameter so that a test can put a partition on either side of a
     * boundary that is two years wide, instead of depending on what month the suite happens to run in.
     */
    void maintain(Instant now) {
        ensurePartitions(now);
        publishRetainedFrom(dropExpired(now));
    }

    void ensurePartitions(Instant now) {
        YearMonth current = YearMonth.from(now.atOffset(ZoneOffset.UTC));
        for (YearMonth month = current.minusMonths(1); !month.isAfter(current.plusMonths(MONTHS_AHEAD)); month = month.plusMonths(1)) {
            for (String table : PARTITIONED_TABLES) {
                createPartition(table, month);
            }
        }
    }

    private void createPartition(String table, YearMonth month) {
        String partition = "%s_%d_%02d".formatted(table, month.getYear(), month.getMonthValue());
        jdbc.sql("""
                create table if not exists analytics.%s partition of analytics.%s
                for values from ('%s-01 00:00:00+00') to ('%s-01 00:00:00+00')
                """.formatted(partition, table, month, month.plusMonths(1)))
                .update();
        log.debug("Partition analytics.{} ensured", partition);
    }

    /**
     * Drops every partition whose whole range is older than the horizon, and answers with the ones that are
     * left.
     * <p>
     * {@code drop table} takes a brief exclusive lock on the parent, which is why this runs at 03:00 UTC and
     * one partition at a time rather than inside one transaction holding all of them. The gentler
     * {@code detach partition concurrently} was not used: it cannot run in a transaction block and it leaves
     * a third state to reason about — detached but not yet dropped — in exchange for microseconds on a
     * pipeline whose writes are batched and retried from Kafka anyway.
     *
     * @return the partitions still attached, which is what the horizon is computed from
     */
    private List<Partition> dropExpired(Instant now) {
        Instant cutoff = cutoff(now);
        List<Partition> partitions = partitions();
        List<Partition> kept = new ArrayList<>(partitions.size());
        for (Partition partition : partitions) {
            if (partition.from() == null || partition.to() == null) {
                // Refusing to drop something whose shape this job does not recognise is the safe direction:
                // the cost is a partition that is never dropped, which the figure below makes visible.
                log.warn("Leaving analytics.{} alone: its bounds are not a range this job recognises",
                        partition.name());
                kept.add(partition);
            } else if (partition.to().isAfter(cutoff)) {
                kept.add(partition);
            } else {
                drop(partition, cutoff);
            }
        }
        return kept;
    }

    /**
     * The first instant of the oldest month still kept: a partition may go when it ends at or before this.
     * Pure and package-visible so the boundary can be tested at an instant rather than at whatever month the
     * suite happens to run in.
     */
    static Instant cutoff(Instant now) {
        return YearMonth.from(now.atOffset(ZoneOffset.UTC))
                .minusMonths(RETENTION_MONTHS)
                .atDay(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    private void drop(Partition partition, Instant cutoff) {
        if (!SAFE_NAME.matcher(partition.name()).matches()) {
            log.warn("Leaving analytics.{} alone: not a name this job will interpolate", partition.name());
            return;
        }
        // Logged before the statement and at INFO: this is the one thing here that cannot be undone, so the
        // record of it should exist even if the statement is what fails. Named with its range rather than
        // with a row count, which would mean scanning the partition to describe deleting it.
        log.info("Dropping analytics.{}, covering {} to {}: entirely older than {}, the {} months of raw data kept",
                partition.name(), partition.from(), partition.to(), cutoff, RETENTION_MONTHS);
        jdbc.sql("drop table analytics.%s".formatted(partition.name())).update();
    }

    /**
     * Records the first day raw data still covers, which is what api refuses ranges against.
     * <p>
     * Taken from the oldest partition that survived rather than from {@link #RETENTION_MONTHS}, so what is
     * published is what the database holds. The two differ in every case that matters: before this job has
     * ever dropped anything, on a database younger than the horizon, and on one carrying a partition this
     * job declined to recognise.
     */
    private void publishRetainedFrom(List<Partition> kept) {
        LocalDate from = kept.stream()
                .map(Partition::from)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .map(at -> at.atOffset(ZoneOffset.UTC).toLocalDate())
                .orElse(null);
        if (from == null) {
            // Unreachable while ensurePartitions runs first, and left as a warning rather than as a write:
            // publishing "no raw data at all" would widen what api offers to everything, which is the
            // direction that answers wrongly.
            log.warn("No partition with a readable range; leaving the published retention horizon alone");
            return;
        }
        jdbc.sql("update analytics.raw_data_retention set retained_from = :from, updated_at = now()")
                .param("from", from)
                .update();
        log.info("Raw events and sessions now reach back to {}", from);
    }

    private List<Partition> partitions() {
        return jdbc.sql(PARTITIONS)
                .param("tables", List.of(PARTITIONED_TABLES))
                .query((rs, row) -> new Partition(
                        rs.getString("name"),
                        instantOf(rs.getObject("lower_bound", OffsetDateTime.class)),
                        instantOf(rs.getObject("upper_bound", OffsetDateTime.class))))
                .list();
    }

    private static Instant instantOf(OffsetDateTime at) {
        return at == null ? null : at.toInstant();
    }

    /** An attached partition and the range it covers; the bounds are null when they are not a plain range. */
    private record Partition(String name, Instant from, Instant to) {
    }
}
