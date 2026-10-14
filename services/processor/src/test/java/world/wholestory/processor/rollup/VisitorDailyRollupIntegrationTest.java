package world.wholestory.processor.rollup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import world.wholestory.contracts.UuidV7;
import world.wholestory.processor.TestcontainersConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim this table rests on, tested rather than argued: a range's unique visitors are the sum of its UTC
 * days' (D-159). It is only true because the hashing salt rotates daily (ADR 0005), so the fixtures below
 * give each day its own hashes exactly as production does — reusing one across two days would be modelling
 * something that cannot happen and would make the sum look wrong.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VisitorDailyRollupIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    VisitorDailyRollup rollup;

    /**
     * The watermark is one row for the whole database, so without this the first test to run would carry it
     * past every later test's fixtures and they would all roll up nothing. Shared state between tests in one
     * class is a trap this project has met before, in Redis keys and Kafka topics.
     */
    /**
     * The newest day the job will count, which is not yesterday for the first hour of each UTC day. Fixtures
     * are placed relative to this rather than to today, so that nothing here depends on what time of day
     * the suite happens to run.
     */
    private static final LocalDate LAST = VisitorDailyRollup.lastDayToCompute(Instant.now());

    @BeforeEach
    void startFromNothingRolledUp() {
        jdbc.sql("delete from analytics.visitor_daily").update();
        jdbc.sql("update analytics.visitor_daily_watermark set covered_through = null").update();
    }

    @Test
    void aRangeOfDaysAddsUpToWhatCountingItLiveWouldGive() {
        UUID site = UUID.randomUUID();
        // Three finished days. Each gets its own hashes, which is what the daily salt guarantees, and the
        // middle day repeats one of its own so that a day's figure is a real distinct count rather than a
        // row count.
        session(site, LAST.minusDays(2), 101L, "TR", "Chrome");
        session(site, LAST.minusDays(2), 102L, "DE", "Firefox");
        session(site, LAST.minusDays(1), 201L, "TR", "Chrome");
        session(site, LAST.minusDays(1), 201L, "TR", "Chrome");
        session(site, LAST, 301L, "TR", "Safari");

        rollup.catchUp();

        long summed = jdbc.sql("""
                        select coalesce(sum(visitors), 0) from analytics.visitor_daily
                        where site_id = ? and dimension = 'TOTAL' and utc_day >= ? and utc_day < ?""")
                .params(site, LAST.minusDays(2), LAST.plusDays(1))
                .query(Long.class).single();
        long live = jdbc.sql("""
                        select count(distinct visitor_hash) from analytics.sessions
                        where site_id = ? and started_at >= ? and started_at < ?""")
                .params(site, at(LAST.minusDays(2)), at(LAST.plusDays(1)))
                .query(Long.class).single();

        assertThat(summed).isEqualTo(live).isEqualTo(4);
    }

    @Test
    void aBreakdownAddsUpTheSameWay() {
        UUID site = UUID.randomUUID();
        session(site, LAST.minusDays(1), 401L, "TR", "Chrome");
        session(site, LAST.minusDays(1), 402L, "DE", "Chrome");
        session(site, LAST, 501L, "TR", "Firefox");

        rollup.catchUp();

        assertThat(visitorsFor(site, "COUNTRY", "TR", LAST.minusDays(1), LAST.plusDays(1))).isEqualTo(2);
        assertThat(visitorsFor(site, "COUNTRY", "DE", LAST.minusDays(1), LAST.plusDays(1))).isEqualTo(1);
        assertThat(visitorsFor(site, "BROWSER", "Chrome", LAST.minusDays(1), LAST.plusDays(1))).isEqualTo(2);
    }

    /**
     * The page is the only rolled up dimension that is not a property of a visit, so it is counted from the
     * events and it is the one a grouping set over the sessions could never have reached. It adds up across
     * days for the same reason the rest do — the salt rotates daily — and the figure is checked against
     * counting the events live rather than against a number written here, because a number written here
     * would only prove the fixture was read twice the same way.
     */
    @Test
    void aPageIsCountedFromTheEventsAndAddsUpTheSameWay() {
        UUID site = UUID.randomUUID();
        session(site, LAST.minusDays(1), 701L, "TR", "Chrome");
        session(site, LAST.minusDays(1), 702L, "TR", "Chrome");
        session(site, LAST, 703L, "TR", "Chrome");

        rollup.catchUp();

        long summed = visitorsFor(site, "PAGE", "/pricing", LAST.minusDays(1), LAST.plusDays(1));
        long live = jdbc.sql("""
                        select count(distinct visitor_hash) from analytics.events
                        where site_id = ? and pathname = '/pricing' and timestamp >= ? and timestamp < ?""")
                .params(site, at(LAST.minusDays(1)), at(LAST.plusDays(1)))
                .query(Long.class).single();

        assertThat(summed).isEqualTo(live).isEqualTo(3);
        // The columns a page row has no answer for are left empty rather than filled with a plausible zero.
        assertThat(jdbc.sql("""
                        select count(*) from analytics.visitor_daily
                        where site_id = ? and dimension = 'PAGE' and visits is null and pageviews is null""")
                .params(site).query(Long.class).single()).isPositive();
    }

    /**
     * A day that has not settled is left alone. Counting it would freeze a figure that is not final, and the
     * insert does nothing on conflict, so it would never be corrected.
     */
    @Test
    void aDayThatHasNotSettledIsLeftAlone() {
        UUID site = UUID.randomUUID();
        session(site, LAST, 601L, "TR", "Chrome");
        session(site, LAST.plusDays(1), 602L, "TR", "Chrome");

        rollup.catchUp();

        assertThat(rowsFor(site, LAST.plusDays(1))).isZero();
        assertThat(rowsFor(site, LAST)).isPositive();
        assertThat(watermark()).isEqualTo(LAST);
    }

    /** A second run must neither double a figure nor move the watermark past what it computed. */
    @Test
    void runningAgainChangesNothing() {
        UUID site = UUID.randomUUID();
        session(site, LAST, 701L, "TR", "Chrome");

        rollup.catchUp();
        long after = visitorsFor(site, "TOTAL", "", LAST, LAST.plusDays(1));
        rollup.catchUp();

        assertThat(visitorsFor(site, "TOTAL", "", LAST, LAST.plusDays(1))).isEqualTo(after).isEqualTo(1);
        assertThat(watermark()).isEqualTo(LAST);
    }

    /** A region's name has to travel with its code, or a breakdown answered from here cannot label its rows. */
    @Test
    void carriesTheLabelWhereTheValueIsACode() {
        UUID site = UUID.randomUUID();
        session(site, LAST, 801L, "TR", "Chrome");

        rollup.catchUp();

        String label = jdbc.sql("""
                        select label from analytics.visitor_daily
                        where site_id = ? and utc_day = ? and dimension = 'REGION'""")
                .params(site, LAST)
                .query(String.class).single();
        assertThat(label).isEqualTo("Ankara");
    }

    /**
     * The half of a contract whose other half is api's {@code Dimension.rolledUp()}. A reader asks this table
     * for a dimension by name, so a dimension it believes is here but is not would not fail — it would return
     * the edges of the range and nothing in between, which is a report that is quietly short. If this list
     * changes, that flag has to change with it, and {@code DimensionTest} pins the same names on that side.
     */
    @Test
    void holdsExactlyTheDimensionsTheReaderIsAllowedToAskFor() {
        UUID site = UUID.randomUUID();
        session(site, LAST, 901L, "TR", "Chrome");

        rollup.catchUp();

        assertThat(jdbc.sql("select distinct dimension from analytics.visitor_daily order by 1")
                .query(String.class).list())
                .containsExactly("BROWSER", "CITY", "COUNTRY", "DEVICE", "OS", "PAGE", "REGION", "SOURCE", "TOTAL");
    }

    private long visitorsFor(UUID site, String dimension, String value, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select coalesce(sum(visitors), 0) from analytics.visitor_daily
                        where site_id = ? and dimension = ? and value = ? and utc_day >= ? and utc_day < ?""")
                .params(site, dimension, value, from, to)
                .query(Long.class).single();
    }

    private long rowsFor(UUID site, LocalDate day) {
        return jdbc.sql("select count(*) from analytics.visitor_daily where site_id = ? and utc_day = ?")
                .params(site, day)
                .query(Long.class).single();
    }

    private LocalDate watermark() {
        return jdbc.sql("select covered_through from analytics.visitor_daily_watermark")
                .query(LocalDate.class).single();
    }

    /**
     * Midday, so that nothing lands on a boundary by accident.
     * <p>
     * The two pageviews are written as well as the session row, because the page dimension is rolled up from
     * the events and a session with no events is not a state this pipeline can produce. Seeding only the
     * session would have modelled something that cannot happen, and the rollup would have looked complete
     * while holding no page rows at all.
     */
    private void session(UUID site, LocalDate day, long visitor, String country, String browser) {
        Instant at = day.atTime(12, 0).toInstant(ZoneOffset.UTC);
        UUID visit = UuidV7.generate(at);
        jdbc.sql("""
                        insert into analytics.sessions
                            (session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                             entry_page, exit_page, country_code, subdivision_code, subdivision_name,
                             city_geoname_id, city_name, browser, os, device_type)
                        values (?, ?, ?, ?, ?, 2, 2, '/', '/pricing', ?, 'TR-06', 'Ankara', 323786, 'Ankara',
                                ?, 'macOS', 'desktop')""")
                .params(visit, at(at), site, visitor, at(at.plus(60, ChronoUnit.SECONDS)), country, browser)
                .update();
        pageview(site, visit, visitor, at, "/");
        pageview(site, visit, visitor, at.plus(60, ChronoUnit.SECONDS), "/pricing");
    }

    private void pageview(UUID site, UUID visit, long visitor, Instant at, String path) {
        jdbc.sql("""
                        insert into analytics.events
                            (event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname)
                        values (?, ?, ?, ?, ?, 'pageview', 'example.test', ?)""")
                .params(UuidV7.generate(at), at(at), site, visit, visitor, path)
                .update();
    }

    private static OffsetDateTime at(LocalDate day) {
        return day.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
