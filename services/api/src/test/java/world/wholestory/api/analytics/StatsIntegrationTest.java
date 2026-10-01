package world.wholestory.api.analytics;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.api.TestcontainersConfiguration;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reporting side. The analytics rows are seeded directly — writing them is processor's job and its own
 * tests cover it — while every read here goes through the {@code api_*} views with api's own role, which is the
 * contract this service depends on (D-012).
 * <p>
 * The site's timezone is Europe/Istanbul throughout, so every assertion about a day is also an assertion that
 * the day was cut where the site says it is and not in UTC (D-020).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatsIntegrationTest {

    private static final String ZONE = "Europe/Istanbul";
    /** 2026-10-01 in Istanbul runs from 2026-09-30T21:00Z to 2026-10-01T21:00Z. */
    private static final String THE_DAY = "2026-10-01";

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    PostgreSQLContainer postgres;

    /** Seeds the analytics tables. api's own role may only read the views, which is exactly the point. */
    private JdbcClient seed;

    @BeforeEach
    void connectForSeeding() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        seed = JdbcClient.create(dataSource);
        ensurePartitions();
    }

    @Test
    void theHeadlineNumbersComeFromTheRightTables() throws Exception {
        Site site = registerSite();
        // Two visitors inside the Istanbul day, one of them twice.
        session(site.id(), 1L, "2026-09-30T22:00:00Z", "2026-09-30T22:03:00Z", 3, 3, "/", "/pricing");
        session(site.id(), 2L, "2026-10-01T09:00:00Z", "2026-10-01T09:00:00Z", 1, 1, "/", "/");
        session(site.id(), 1L, "2026-10-01T18:00:00Z", "2026-10-01T18:01:00Z", 2, 2, "/docs", "/docs");
        pageviews(site.id(), "2026-09-30T22:00:00Z", "/", 3);
        pageviews(site.id(), "2026-10-01T09:00:00Z", "/", 1);
        pageviews(site.id(), "2026-10-01T18:00:00Z", "/docs", 2);

        mvc.perform(stats(site, "summary"))
                .andExpect(status().isOk())
                // Two people, three visits: a visitor is counted once however many times they came back.
                .andExpect(jsonPath("$.visitors").value(2))
                .andExpect(jsonPath("$.visits").value(3))
                .andExpect(jsonPath("$.pageviews").value(6))
                // One of the three visits saw a single page and did nothing else.
                .andExpect(jsonPath("$.bounceRate").value(1.0 / 3))
                .andExpect(jsonPath("$.averageVisitDuration").value((180 + 0 + 60) / 3.0));
    }

    /** The same moment belongs to different days in different zones; this is the case that catches a UTC bug. */
    @Test
    void aVisitJustBeforeTheLocalMidnightBelongsToThePreviousDay() throws Exception {
        Site site = registerSite();
        // 2026-09-30T20:00Z is 23:00 on 30 September in Istanbul: the day before.
        session(site.id(), 10L, "2026-09-30T20:00:00Z", "2026-09-30T20:00:00Z", 1, 1, "/", "/");
        // 2026-09-30T22:00Z is 01:00 on 1 October in Istanbul: the day asked for.
        session(site.id(), 11L, "2026-09-30T22:00:00Z", "2026-09-30T22:00:00Z", 1, 1, "/", "/");

        mvc.perform(stats(site, "summary"))
                .andExpect(jsonPath("$.visitors").value(1))
                .andExpect(jsonPath("$.visits").value(1));

        mvc.perform(stats(site, "summary", "2026-09-30", "2026-09-30", site.session()))
                .andExpect(jsonPath("$.visitors").value(1))
                .andExpect(jsonPath("$.visits").value(1));

        // Both days together hold both visits, which is the proof neither was counted twice.
        mvc.perform(stats(site, "summary", "2026-09-30", "2026-10-01", site.session()))
                .andExpect(jsonPath("$.visitors").value(2))
                .andExpect(jsonPath("$.visits").value(2));
    }

    @Test
    void aSingleDayIsReadHourByHourAndEmptyHoursAreStillThere() throws Exception {
        Site site = registerSite();
        session(site.id(), 20L, "2026-10-01T09:00:00Z", "2026-10-01T09:00:00Z", 1, 1, "/", "/");
        pageviews(site.id(), "2026-10-01T09:00:00Z", "/", 4);

        MvcResult result = mvc.perform(stats(site, "timeseries"))
                .andExpect(status().isOk())
                // A day in Istanbul is 24 hours, and a chart needs every one of them.
                .andExpect(jsonPath("$.length()").value(24))
                .andReturn();

        var points = jsonMapper.readTree(result.getResponse().getContentAsString());
        var withTraffic = java.util.stream.StreamSupport
                .stream(points.spliterator(), false)
                .filter(point -> point.get("visitors").asInt() > 0)
                .toList();
        assertThat(withTraffic).hasSize(1);
        assertThat(withTraffic.getFirst().get("bucket").asString()).startsWith("2026-10-01T09:00");
        assertThat(withTraffic.getFirst().get("pageviews").asInt()).isEqualTo(4);
    }

    @Test
    void aLongerRangeIsReadDayByDayInTheSitesZone() throws Exception {
        Site site = registerSite();
        session(site.id(), 30L, "2026-09-30T22:00:00Z", "2026-09-30T22:00:00Z", 1, 1, "/", "/");

        mvc.perform(stats(site, "timeseries", "2026-10-01", "2026-10-07", site.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                // The first bucket starts at the Istanbul midnight, which is 21:00 UTC the day before.
                .andExpect(jsonPath("$[0].bucket").value("2026-09-30T21:00:00Z"))
                .andExpect(jsonPath("$[0].visitors").value(1))
                .andExpect(jsonPath("$[1].visitors").value(0));
    }

    @Test
    void sessionDimensionsAreBrokenDownInOnePass() throws Exception {
        Site site = registerSite();
        sessionWith(site.id(), 40L, "2026-10-01T09:00:00Z", "TR", "TR-06", "Ankara", 323786, "Chrome", "desktop", "Google");
        sessionWith(site.id(), 41L, "2026-10-01T10:00:00Z", "TR", "TR-34", "Istanbul", 745044, "Chrome", "mobile", "Google");
        sessionWith(site.id(), 42L, "2026-10-01T11:00:00Z", "DE", "DE-BE", "Berlin", 2950159, "Firefox", "desktop", null);

        mvc.perform(breakdown(site, "COUNTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("TR"))
                .andExpect(jsonPath("$[0].visitors").value(2))
                // A country code needs no name from us: the browser can say "Turkey" itself.
                .andExpect(jsonPath("$[0].label").value("TR"))
                .andExpect(jsonPath("$[1].key").value("DE"));

        mvc.perform(breakdown(site, "CITY"))
                .andExpect(jsonPath("$.length()").value(3))
                // The key stays the geoname id, so it can be filtered on; the label is what a person reads.
                .andExpect(jsonPath("$[?(@.key == '323786')].label").value("Ankara"));

        mvc.perform(breakdown(site, "REGION"))
                .andExpect(jsonPath("$[?(@.key == 'TR-06')].label").value("Ankara"));

        mvc.perform(breakdown(site, "BROWSER"))
                .andExpect(jsonPath("$[0].key").value("Chrome"))
                .andExpect(jsonPath("$[0].visitors").value(2));

        mvc.perform(breakdown(site, "DEVICE")).andExpect(jsonPath("$.length()").value(2));

        // The visit with no known source is still counted, under an empty key the client prints as unknown.
        mvc.perform(breakdown(site, "SOURCE"))
                .andExpect(jsonPath("$[?(@.key == '')].visitors").value(1))
                .andExpect(jsonPath("$[?(@.key == 'Google')].visitors").value(2));
    }

    /** A page belongs to a pageview, not to a visit, so this breakdown is grouped from the events. */
    @Test
    void pagesAreBrokenDownFromTheEventsWithBothVisitorsAndPageviews() throws Exception {
        Site site = registerSite();
        UUID visit = UUID.randomUUID();
        session(site.id(), 50L, "2026-10-01T09:00:00Z", "2026-10-01T09:05:00Z", 3, 3, "/", "/pricing", visit);
        event(site.id(), visit, 50L, "2026-10-01T09:00:00Z", "pageview", "/");
        event(site.id(), visit, 50L, "2026-10-01T09:02:00Z", "pageview", "/pricing");
        event(site.id(), visit, 50L, "2026-10-01T09:05:00Z", "pageview", "/pricing");

        mvc.perform(breakdown(site, "PAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == '/pricing')].pageviews").value(2))
                .andExpect(jsonPath("$[?(@.key == '/pricing')].visitors").value(1))
                .andExpect(jsonPath("$[?(@.key == '/pricing')].visits").value(1));
    }

    @Test
    void theBreakdownSizeIsCapped() throws Exception {
        Site site = registerSite();

        mvc.perform(breakdown(site, "PAGE").param("limit", "100000")).andExpect(status().isOk());
        mvc.perform(breakdown(site, "NONSENSE")).andExpect(status().isBadRequest());
    }

    @Test
    void anotherOrganizationSeesNothingAtAll() throws Exception {
        Site site = registerSite();
        session(site.id(), 60L, "2026-10-01T09:00:00Z", "2026-10-01T09:00:00Z", 1, 1, "/", "/");
        Site other = registerSite();

        mvc.perform(stats(site, "summary", THE_DAY, THE_DAY, other.session())).andExpect(status().isNotFound());
        mvc.perform(stats(site, "timeseries", THE_DAY, THE_DAY, other.session())).andExpect(status().isNotFound());
        mvc.perform(breakdown(site, "PAGE", other.session())).andExpect(status().isNotFound());
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private void ensurePartitions() {
        for (String month : new String[]{"2026_09", "2026_10"}) {
            String from = month.replace('_', '-') + "-01";
            String to = month.equals("2026_09") ? "2026-10-01" : "2026-11-01";
            for (String table : new String[]{"events", "sessions"}) {
                seed.sql(("create table if not exists analytics.%s_%s partition of analytics.%s "
                        + "for values from ('%s 00:00:00+00') to ('%s 00:00:00+00')")
                        .formatted(table, month, table, from, to)).update();
            }
        }
    }

    private void session(UUID siteId, long visitor, String startedAt, String endedAt,
                         int pageviews, int events, String entry, String exit) {
        session(siteId, visitor, startedAt, endedAt, pageviews, events, entry, exit, UUID.randomUUID());
    }

    private void session(UUID siteId, long visitor, String startedAt, String endedAt,
                         int pageviews, int events, String entry, String exit, UUID sessionId) {
        seed.sql("""
                        insert into analytics.sessions
                            (session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                             entry_page, exit_page)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(sessionId, at(startedAt), siteId, visitor, at(endedAt), pageviews, events, entry, exit)
                .update();
    }

    private void sessionWith(UUID siteId, long visitor, String startedAt, String country, String region,
                             String city, int cityId, String browser, String device, String source) {
        seed.sql("""
                        insert into analytics.sessions
                            (session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                             entry_page, exit_page, country_code, subdivision_code, subdivision_name,
                             city_geoname_id, city_name, browser, device_type, referrer_source)
                        values (?, ?, ?, ?, ?, 1, 1, '/', '/', ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(UUID.randomUUID(), at(startedAt), siteId, visitor, at(startedAt),
                        country, region, city, cityId, city, browser, device, source)
                .update();
    }

    private void event(UUID siteId, UUID sessionId, long visitor, String timestamp, String name, String path) {
        seed.sql("""
                        insert into analytics.events
                            (event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname)
                        values (?, ?, ?, ?, ?, ?, 'example.com', ?)""")
                .params(UUID.randomUUID(), at(timestamp), siteId, sessionId, visitor, name, path)
                .update();
    }

    private void pageviews(UUID siteId, String hour, String path, int count) {
        seed.sql("insert into analytics.page_hourly (site_id, hour, pathname, pageviews) values (?, ?, ?, ?)")
                .params(siteId, at(hour), path, (long) count)
                .update();
    }

    /**
     * Every request is built whole. MockMvc's {@code param} and {@code cookie} append rather than replace, so
     * reusing a half-built request and overriding a value sends both — which is how one of these tests passed
     * for the wrong reason until it was caught.
     */
    private MockHttpServletRequestBuilder stats(Site site, String report) {
        return stats(site, report, THE_DAY, THE_DAY, site.session());
    }

    private MockHttpServletRequestBuilder stats(Site site, String report, String from, String to, Cookie session) {
        return get("/api/sites/{siteId}/stats/{report}", site.id(), report)
                .param("from", from)
                .param("to", to)
                .cookie(session);
    }

    private MockHttpServletRequestBuilder breakdown(Site site, String dimension) {
        return breakdown(site, dimension, site.session());
    }

    private MockHttpServletRequestBuilder breakdown(Site site, String dimension, Cookie session) {
        return get("/api/sites/{siteId}/stats/breakdown", site.id())
                .param("dimension", dimension)
                .param("from", THE_DAY)
                .param("to", THE_DAY)
                .cookie(session);
    }

    private Site registerSite() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        MvcResult account = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "name": "Owner", "password": "correct horse battery"}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie session = account.getResponse().getCookie("SESSION");
        UUID organizationId = UUID.fromString(jsonMapper.readTree(account.getResponse().getContentAsString())
                .get("organizationId").asString());

        String domain = "d" + UUID.randomUUID().toString().replace("-", "") + ".example.com";
        MvcResult created = mvc.perform(post("/api/sites")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationId": "%s", "domain": "%s", "timezone": "%s"}"""
                                .formatted(organizationId, domain, ZONE)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID siteId = UUID.fromString(jsonMapper.readTree(created.getResponse().getContentAsString())
                .get("siteId").asString());
        return new Site(siteId, session);
    }

    private static OffsetDateTime at(String instant) {
        return Instant.parse(instant).atOffset(ZoneOffset.UTC);
    }

    private record Site(UUID id, Cookie session) {
    }
}
