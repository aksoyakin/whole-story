package world.wholestory.api.site;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Goals end to end: defined through Site Management, reported by Analytics over the events processor wrote.
 * <p>
 * The analytics rows are seeded directly, as in {@code StatsIntegrationTest}, while every read goes through the
 * {@code api_*} views with api's own role. The site's timezone is Europe/Istanbul throughout, so the day being
 * asked about is the site's own day and not UTC's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GoalIntegrationTest {

    private static final String ZONE = "Europe/Istanbul";
    /** 2026-10-01 in Istanbul runs from 2026-09-30T21:00Z to 2026-10-01T21:00Z. */
    private static final String THE_DAY = "2026-10-01";

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    PostgreSQLContainer postgres;

    private JdbcClient seed;

    @BeforeEach
    void connectForSeeding() {
        seed = JdbcClient.create(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        for (String table : new String[]{"events", "sessions"}) {
            seed.sql(("create table if not exists analytics.%s_2026_10 partition of analytics.%s "
                    + "for values from ('2026-10-01 00:00:00+00') to ('2026-11-01 00:00:00+00')")
                    .formatted(table, table)).update();
            seed.sql(("create table if not exists analytics.%s_2026_09 partition of analytics.%s "
                    + "for values from ('2026-09-01 00:00:00+00') to ('2026-10-01 00:00:00+00')")
                    .formatted(table, table)).update();
        }
    }

    @Test
    void anEventGoalCountsTheVisitorsWhoDidItAndHowOftenItHappened() throws Exception {
        Site site = registerSite();
        // Three visitors came; two of them signed up, one of those twice.
        visit(site, 1L, "2026-10-01T09:00:00Z");
        visit(site, 2L, "2026-10-01T10:00:00Z");
        visit(site, 3L, "2026-10-01T11:00:00Z");
        event(site, 1L, "2026-10-01T09:01:00Z", "Signup", "/pricing");
        event(site, 2L, "2026-10-01T10:01:00Z", "Signup", "/pricing");
        event(site, 2L, "2026-10-01T10:02:00Z", "Signup", "/pricing");

        defineGoal(site, "EVENT", "Signup");

        mvc.perform(report(site))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("EVENT"))
                .andExpect(jsonPath("$[0].target").value("Signup"))
                // Two people converted, three times in total, out of three visitors.
                .andExpect(jsonPath("$[0].visitors").value(2))
                .andExpect(jsonPath("$[0].completions").value(3))
                .andExpect(jsonPath("$[0].rate").value(2.0 / 3));
    }

    @Test
    void aPageGoalMatchesTheExactPath() throws Exception {
        Site site = registerSite();
        visit(site, 10L, "2026-10-01T09:00:00Z");
        visit(site, 11L, "2026-10-01T10:00:00Z");
        event(site, 10L, "2026-10-01T09:00:00Z", "pageview", "/thanks");
        event(site, 11L, "2026-10-01T10:00:00Z", "pageview", "/thanks-again");

        defineGoal(site, "PAGEVIEW", "/thanks");

        mvc.perform(report(site))
                .andExpect(jsonPath("$[0].visitors").value(1))
                .andExpect(jsonPath("$[0].completions").value(1));
    }

    /** The reason the wildcard exists: "any blog post" is one goal, not one goal per post. */
    @Test
    void aPageGoalWithAWildcardMatchesEveryPathUnderIt() throws Exception {
        Site site = registerSite();
        visit(site, 20L, "2026-10-01T09:00:00Z");
        visit(site, 21L, "2026-10-01T10:00:00Z");
        event(site, 20L, "2026-10-01T09:00:00Z", "pageview", "/blog/privacy");
        event(site, 21L, "2026-10-01T10:00:00Z", "pageview", "/blog/cookies");
        event(site, 21L, "2026-10-01T10:05:00Z", "pageview", "/pricing");

        defineGoal(site, "PAGEVIEW", "/blog/*");

        mvc.perform(report(site))
                .andExpect(jsonPath("$[0].visitors").value(2))
                .andExpect(jsonPath("$[0].completions").value(2));
    }

    /**
     * SQL's own wildcard inside a path must not leak into the match, or this goal would also count
     * {@code /100-off} and {@code /1000-free}.
     */
    @Test
    void aPathContainingSqlsWildcardStillMatchesOnlyItself() throws Exception {
        Site site = registerSite();
        visit(site, 30L, "2026-10-01T09:00:00Z");
        event(site, 30L, "2026-10-01T09:00:00Z", "pageview", "/100-off");
        event(site, 30L, "2026-10-01T09:01:00Z", "pageview", "/100%-free");

        defineGoal(site, "PAGEVIEW", "/100%-free");

        mvc.perform(report(site))
                .andExpect(jsonPath("$[0].completions").value(1));
    }

    @Test
    void aGoalNobodyCompletedIsStillReported() throws Exception {
        Site site = registerSite();
        visit(site, 40L, "2026-10-01T09:00:00Z");
        defineGoal(site, "EVENT", "Signup");

        mvc.perform(report(site))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].visitors").value(0))
                .andExpect(jsonPath("$[0].completions").value(0))
                .andExpect(jsonPath("$[0].rate").value(0.0));
    }

    @Test
    void aSiteWithoutGoalsReportsAnEmptyList() throws Exception {
        Site site = registerSite();

        mvc.perform(report(site)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    /** A goal is a question, so it answers about events that were recorded long before it was defined. */
    @Test
    void aGoalDefinedTodayReportsOnWhatAlreadyHappened() throws Exception {
        Site site = registerSite();
        visit(site, 50L, "2026-10-01T09:00:00Z");
        event(site, 50L, "2026-10-01T09:01:00Z", "Signup", "/pricing");

        defineGoal(site, "EVENT", "Signup");

        mvc.perform(report(site)).andExpect(jsonPath("$[0].completions").value(1));
    }

    @Test
    void aFilterNarrowsTheConversionsAndTheRateWithThem() throws Exception {
        Site site = registerSite();
        visitFrom(site, 60L, "2026-10-01T09:00:00Z", "TR", "Chrome");
        visitFrom(site, 61L, "2026-10-01T10:00:00Z", "DE", "Chrome");
        // The geo and device fields are denormalised onto events as well as sessions, which is what lets a
        // filter narrow both the conversions and the visitors they are a share of.
        eventFrom(site, 60L, "2026-10-01T09:01:00Z", "Signup", "/pricing", "TR", "Chrome");
        eventFrom(site, 61L, "2026-10-01T10:01:00Z", "Signup", "/pricing", "DE", "Chrome");
        defineGoal(site, "EVENT", "Signup");

        mvc.perform(report(site))
                .andExpect(jsonPath("$[0].visitors").value(2))
                .andExpect(jsonPath("$[0].rate").value(1.0));

        // One visitor from Turkey, who converted: both the numerator and the denominator narrow.
        mvc.perform(filteredReport(site, "COUNTRY:TR"))
                .andExpect(jsonPath("$[0].visitors").value(1))
                .andExpect(jsonPath("$[0].rate").value(1.0));

        mvc.perform(filteredReport(site, "COUNTRY:FR"))
                .andExpect(jsonPath("$[0].visitors").value(0))
                .andExpect(jsonPath("$[0].rate").value(0.0));
    }

    @Test
    void everyGoalIsAnsweredInOneRequest() throws Exception {
        Site site = registerSite();
        visit(site, 70L, "2026-10-01T09:00:00Z");
        event(site, 70L, "2026-10-01T09:00:00Z", "pageview", "/thanks");
        event(site, 70L, "2026-10-01T09:01:00Z", "Signup", "/thanks");
        defineGoal(site, "EVENT", "Signup");
        defineGoal(site, "PAGEVIEW", "/thanks");

        mvc.perform(report(site))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.target == 'Signup')].completions").value(1))
                .andExpect(jsonPath("$[?(@.target == '/thanks')].completions").value(1));
    }

    // --- defining them ------------------------------------------------------------------------------------

    @Test
    void theSameTargetCannotBeAGoalTwice() throws Exception {
        Site site = registerSite();
        defineGoal(site, "EVENT", "Signup");

        mvc.perform(goalRequest(site, "EVENT", "Signup")).andExpect(status().isConflict());
        // The same text as a page goal is a different goal, which the index allows.
        mvc.perform(goalRequest(site, "PAGEVIEW", "/Signup")).andExpect(status().isCreated());
    }

    @Test
    void aTargetThatCouldNeverMatchIsRefused() throws Exception {
        Site site = registerSite();

        mvc.perform(goalRequest(site, "PAGEVIEW", "/thanks?ref=mail")).andExpect(status().isBadRequest());
        mvc.perform(goalRequest(site, "PAGEVIEW", "thanks")).andExpect(status().isBadRequest());
        mvc.perform(goalRequest(site, "EVENT", " ")).andExpect(status().isBadRequest());
    }

    @Test
    void goalsAreListedAndRemovedBySite() throws Exception {
        Site site = registerSite();
        String goalId = defineGoal(site, "EVENT", "Signup");

        mvc.perform(get("/api/sites/{siteId}/goals", site.id()).cookie(site.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].target").value("Signup"));

        mvc.perform(delete("/api/sites/{siteId}/goals/{goalId}", site.id(), goalId).cookie(site.session()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/sites/{siteId}/goals", site.id()).cookie(site.session()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherOrganizationCanNeitherSeeNorTouchTheGoals() throws Exception {
        Site site = registerSite();
        String goalId = defineGoal(site, "EVENT", "Signup");
        Site stranger = registerSite();

        mvc.perform(get("/api/sites/{siteId}/goals", site.id()).cookie(stranger.session()))
                .andExpect(status().isNotFound());
        mvc.perform(goalRequestAs(site, stranger.session(), "EVENT", "Other"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/sites/{siteId}/goals/{goalId}", site.id(), goalId).cookie(stranger.session()))
                .andExpect(status().isNotFound());
        mvc.perform(reportAs(site, stranger.session())).andExpect(status().isNotFound());

        // And the goal is still there.
        mvc.perform(get("/api/sites/{siteId}/goals", site.id()).cookie(site.session()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** A goal of another site answers as one that does not exist, even when both sites are the caller's. */
    @Test
    void aGoalCannotBeRemovedThroughAnotherOfYourOwnSites() throws Exception {
        Site first = registerSite();
        String goalId = defineGoal(first, "EVENT", "Signup");
        Site second = registerSiteFor(first);

        mvc.perform(delete("/api/sites/{siteId}/goals/{goalId}", second.id(), goalId).cookie(first.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void goalsNeedASession() throws Exception {
        Site site = registerSite();

        mvc.perform(get("/api/sites/{siteId}/goals", site.id())).andExpect(status().isUnauthorized());
        mvc.perform(reportAs(site, null)).andExpect(status().isUnauthorized());
        mvc.perform(reportAs(site, new Cookie("SESSION", "stale"))).andExpect(status().isUnauthorized());
    }

    // --- fixtures -----------------------------------------------------------------------------------------

    /**
     * Every request is built whole, session and filters included. MockMvc's {@code cookie} and {@code param}
     * append rather than replace, so handing a half-built request a second session sends both and the call
     * succeeds as the first one — which is how two of these tests passed for the wrong reason until it was caught.
     */
    private MockHttpServletRequestBuilder report(Site site) {
        return reportAs(site, site.session());
    }

    private MockHttpServletRequestBuilder reportAs(Site site, Cookie session) {
        MockHttpServletRequestBuilder request = get("/api/sites/{siteId}/stats/goals", site.id())
                .param("from", THE_DAY)
                .param("to", THE_DAY);
        return session == null ? request : request.cookie(session);
    }

    private MockHttpServletRequestBuilder filteredReport(Site site, String filter) {
        return reportAs(site, site.session()).param("filter", filter);
    }

    private MockHttpServletRequestBuilder goalRequest(Site site, String type, String target) {
        return goalRequestAs(site, site.session(), type, target);
    }

    private MockHttpServletRequestBuilder goalRequestAs(Site site, Cookie session, String type, String target) {
        MockHttpServletRequestBuilder request = post("/api/sites/{siteId}/goals", site.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "target": "%s"}""".formatted(type, target));
        return session == null ? request : request.cookie(session);
    }

    private String defineGoal(Site site, String type, String target) throws Exception {
        MvcResult result = mvc.perform(goalRequest(site, type, target))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("goalId").asString();
    }

    private void visit(Site site, long visitor, String at) {
        visitFrom(site, visitor, at, null, null);
    }

    private void visitFrom(Site site, long visitor, String at, String country, String browser) {
        seed.sql("""
                        insert into analytics.sessions
                            (session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                             entry_page, exit_page, country_code, browser)
                        values (?, ?, ?, ?, ?, 1, 1, '/', '/', ?, ?)""")
                .params(UUID.randomUUID(), at(at), site.id(), visitor, at(at), country, browser)
                .update();
    }

    private void event(Site site, long visitor, String at, String name, String path) {
        eventFrom(site, visitor, at, name, path, null, null);
    }

    private void eventFrom(Site site, long visitor, String at, String name, String path,
                           String country, String browser) {
        seed.sql("""
                        insert into analytics.events
                            (event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname,
                             country_code, browser)
                        values (?, ?, ?, ?, ?, ?, 'example.com', ?, ?, ?)""")
                .params(UUID.randomUUID(), at(at), site.id(), UUID.randomUUID(), visitor, name, path,
                        country, browser)
                .update();
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
        return new Site(addSite(session, organizationId), session, organizationId);
    }

    /** A second site in the same organization, for the case where both sites are the caller's. */
    private Site registerSiteFor(Site owner) throws Exception {
        return new Site(addSite(owner.session(), owner.organizationId()), owner.session(), owner.organizationId());
    }

    private UUID addSite(Cookie session, UUID organizationId) throws Exception {
        String domain = "d" + UUID.randomUUID().toString().replace("-", "") + ".example.com";
        MvcResult created = mvc.perform(post("/api/sites")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationId": "%s", "domain": "%s", "timezone": "%s"}"""
                                .formatted(organizationId, domain, ZONE)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(jsonMapper.readTree(created.getResponse().getContentAsString())
                .get("siteId").asString());
    }

    private static OffsetDateTime at(String instant) {
        return Instant.parse(instant).atOffset(ZoneOffset.UTC);
    }

    private record Site(UUID id, Cookie session, UUID organizationId) {
    }
}
