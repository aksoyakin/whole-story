package world.wholestory.processor.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import world.wholestory.processor.TestcontainersConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dropping expired partitions, which is the half of D-033 that was never built.
 * <p>
 * The only test there will be for a while: the production database was created in October 2026, so nothing
 * in it becomes droppable until late 2028. That is an argument for testing the boundary here rather than for
 * trusting it — the statement this job runs cannot be undone, and the live proof is two years away.
 * <p>
 * Every partition this class creates is removed afterwards. Tests in one class share a database, and a
 * partition left behind would be a range another test cannot create (this project has met the shared-state
 * trap in Redis keys and Kafka topics already).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PartitionRetentionIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    PartitionMaintenance maintenance;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestCreated() {
        for (String partition : created) {
            jdbc.sql("drop table if exists analytics.%s".formatted(partition)).update();
        }
        created.clear();
    }

    @Test
    void dropsAMonthEntirelyOlderThanTheHorizonAndKeepsTheOneOnTheBoundary() {
        Instant now = Instant.now();
        // The oldest month the horizon still covers, and the one that falls just outside it. Derived from the
        // cutoff rather than written down, so the test says what the rule is instead of restating a date.
        YearMonth oldestKept = YearMonth.from(PartitionMaintenance.cutoff(now).atOffset(ZoneOffset.UTC));
        YearMonth justOutside = oldestKept.minusMonths(1);
        partition("events", justOutside);
        partition("events", oldestKept);
        partition("sessions", justOutside);

        maintenance.maintain(now);

        assertThat(exists(name("events", justOutside))).isFalse();
        assertThat(exists(name("sessions", justOutside))).isFalse();
        // A partition is dropped when all of it is older than the horizon, never when part of it is: this one
        // begins exactly at the cutoff.
        assertThat(exists(name("events", oldestKept))).isTrue();
        // And the months the pipeline is actually writing to are untouched, which is the failure that would
        // stop ingestion outright: there is no DEFAULT partition to catch a row whose month went missing.
        assertThat(exists(name("events", YearMonth.from(now.atOffset(ZoneOffset.UTC))))).isTrue();
    }

    @Test
    void publishesTheFirstDayTheRawDataStillCovers() {
        Instant now = Instant.now();
        YearMonth oldestKept = YearMonth.from(PartitionMaintenance.cutoff(now).atOffset(ZoneOffset.UTC));
        partition("events", oldestKept.minusMonths(1));
        partition("events", oldestKept);

        maintenance.maintain(now);

        // What api refuses ranges against. It has to be the oldest partition that survived rather than
        // "today minus the retention", because those differ whenever the job has not dropped anything yet —
        // which is the state every database starts in and the one production is in now.
        assertThat(retainedFrom()).isEqualTo(oldestKept.atDay(1));
    }

    @Test
    void leavesAPartitionWhoseBoundsItCannotReadAlone() {
        Instant now = Instant.now();
        YearMonth oldestKept = YearMonth.from(PartitionMaintenance.cutoff(now).atOffset(ZoneOffset.UTC));
        // An open-ended partition, which is a shape hand-written DDL produces and this job does not
        // recognise (D-145 is why hand-written DDL is a case worth covering). Refusing to drop it is the safe
        // direction: the cost is a partition kept forever, and the horizon below is what makes that visible.
        String openEnded = "events_before_" + oldestKept.minusMonths(1).getYear();
        created.add(openEnded);
        jdbc.sql("""
                        create table analytics.%s partition of analytics.events
                        for values from (MINVALUE) to ('%s-01 00:00:00+00')
                        """.formatted(openEnded, oldestKept.minusMonths(1)))
                .update();

        maintenance.maintain(now);

        assertThat(exists(openEnded)).isTrue();
        // It is also excluded from the published horizon, which would otherwise claim raw data reaches back
        // to the beginning of time.
        assertThat(retainedFrom()).isNotNull().isAfterOrEqualTo(oldestKept.atDay(1));
    }

    private void partition(String table, YearMonth month) {
        String partition = name(table, month);
        created.add(partition);
        jdbc.sql("""
                        create table if not exists analytics.%s partition of analytics.%s
                        for values from ('%s-01 00:00:00+00') to ('%s-01 00:00:00+00')
                        """.formatted(partition, table, month, month.plusMonths(1)))
                .update();
    }

    private static String name(String table, YearMonth month) {
        return "%s_%d_%02d".formatted(table, month.getYear(), month.getMonthValue());
    }

    private boolean exists(String partition) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        select exists (select 1 from pg_class c
                                       join pg_namespace n on n.oid = c.relnamespace
                                       where n.nspname = 'analytics' and c.relname = :name)""")
                .param("name", partition)
                .query(Boolean.class)
                .single());
    }

    private LocalDate retainedFrom() {
        return jdbc.sql("select retained_from from analytics.raw_data_retention")
                .query(LocalDate.class)
                .optional()
                .orElse(null);
    }
}
