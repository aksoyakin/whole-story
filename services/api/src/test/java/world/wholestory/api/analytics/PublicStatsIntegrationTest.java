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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard a site owner chose to make public. Every request here is made <em>without a session</em>,
 * which is the whole point: these are the only reporting endpoints that answer to nobody in particular.
 * <p>
 * What decides the answer is the site's own sharing flag. A site that is not shared has to be
 * indistinguishable from a domain nobody tracks, or the URL becomes a way of finding out which sites exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicStatsIntegrationTest {

    private static final String ZONE = "Europe/Istanbul";
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
            seed.sql(("create table if not exists analytics.%s_2026_09 partition of analytics.%s "
                    + "for values from ('2026-09-01 00:00:00+00') to ('2026-10-01 00:00:00+00')")
                    .formatted(table, table)).update();
            seed.sql(("create table if not exists analytics.%s_2026_10 partition of analytics.%s "
                    + "for values from ('2026-10-01 00:00:00+00') to ('2026-11-01 00:00:00+00')")
                    .formatted(table, table)).update();
        }
    }

    @Test
    void aSharedDashboardIsReadableByAnybody() throws Exception {
        Site site = registerSite();
        session(site.id(), 1L, "2026-10-01T09:00:00Z");
        session(site.id(), 2L, "2026-10-01T10:00:00Z");
        share(site, true);

        // No cookie on any of these.
        mvc.perform(get("/api/public/sites/{domain}", site.domain()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value(site.domain()))
                .andExpect(jsonPath("$.timezone").value(ZONE));

        mvc.perform(publicStats(site, "summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitors").value(2))
                .andExpect(jsonPath("$.visits").value(2));

        mvc.perform(publicStats(site, "timeseries")).andExpect(status().isOk());
        mvc.perform(publicStats(site, "goals")).andExpect(status().isOk());
        mvc.perform(publicStats(site, "breakdown").param("dimension", "BROWSER"))
                .andExpect(status().isOk());
    }

    /** The default. Until the owner says otherwise their numbers are theirs. */
    @Test
    void aSiteThatIsNotSharedAnswersLikeOneThatDoesNotExist() throws Exception {
        Site site = registerSite();
        session(site.id(), 10L, "2026-10-01T09:00:00Z");

        mvc.perform(get("/api/public/sites/{domain}", site.domain())).andExpect(status().isNotFound());
        mvc.perform(publicStats(site, "summary")).andExpect(status().isNotFound());
        mvc.perform(publicStats(site, "timeseries")).andExpect(status().isNotFound());
        mvc.perform(publicStats(site, "goals")).andExpect(status().isNotFound());
    }

    @Test
    void sharingCanBeTakenBackAgain() throws Exception {
        Site site = registerSite();
        share(site, true);
        mvc.perform(publicStats(site, "summary")).andExpect(status().isOk());

        share(site, false);

        mvc.perform(publicStats(site, "summary")).andExpect(status().isNotFound());
    }

    @Test
    void removingTheSiteClosesItsPublicDashboard() throws Exception {
        Site site = registerSite();
        share(site, true);
        mvc.perform(publicStats(site, "summary")).andExpect(status().isOk());

        mvc.perform(delete("/api/sites/{siteId}", site.id()).cookie(site.session()))
                .andExpect(status().isNoContent());

        mvc.perform(publicStats(site, "summary")).andExpect(status().isNotFound());
    }

    /** The shared URL is typed by people, so it has to forgive what the domain value object already forgives. */
    @Test
    void theDomainInTheUrlIsMatchedTheWayItIsStored() throws Exception {
        Site site = registerSite();
        share(site, true);

        mvc.perform(get("/api/public/sites/{domain}", "WWW." + site.domain().toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value(site.domain()));
    }

    /** Not a domain at all is still just a domain we do not track: the caller must not learn the difference. */
    @Test
    void anUnknownOrMalformedDomainIsRefusedTheSameWay() throws Exception {
        mvc.perform(get("/api/public/sites/{domain}", "nobody-tracks-this.example"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/sites/{domain}", "not a domain"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/sites/{domain}/stats/summary", "not a domain")
                        .param("from", THE_DAY).param("to", THE_DAY))
                .andExpect(status().isNotFound());
    }

    /** The shared page narrows the same way the owner's does, so the filter rules have to be the same ones. */
    @Test
    void theSharedDashboardFiltersAndRefusesNonsenseLikeTheOwnersDoes() throws Exception {
        Site site = registerSite();
        sessionFrom(site.id(), 20L, "2026-10-01T09:00:00Z", "TR");
        sessionFrom(site.id(), 21L, "2026-10-01T10:00:00Z", "DE");
        share(site, true);

        mvc.perform(publicStats(site, "summary").param("filter", "COUNTRY:TR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitors").value(1));

        mvc.perform(publicStats(site, "summary").param("filter", "NONSENSE:x"))
                .andExpect(status().isBadRequest());
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder publicStats(Site site, String report) {
        return get("/api/public/sites/{domain}/stats/{report}", site.domain(), report)
                .param("from", THE_DAY)
                .param("to", THE_DAY);
    }

    private void share(Site site, boolean on) throws Exception {
        mvc.perform(put("/api/sites/{siteId}/settings", site.id())
                        .cookie(site.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timezone": "%s", "publicDashboard": %s}""".formatted(ZONE, on)))
                .andExpect(status().isOk());
    }

    private void session(UUID siteId, long visitor, String startedAt) {
        sessionFrom(siteId, visitor, startedAt, null);
    }

    private void sessionFrom(UUID siteId, long visitor, String startedAt, String country) {
        seed.sql("""
                        insert into analytics.sessions
                            (session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                             entry_page, exit_page, country_code, browser)
                        values (?, ?, ?, ?, ?, 1, 1, '/', '/', ?, 'Chrome')""")
                .params(UUID.randomUUID(), at(startedAt), siteId, visitor, at(startedAt), country)
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
        return new Site(siteId, domain, session);
    }

    private static OffsetDateTime at(String instant) {
        return Instant.parse(instant).atOffset(ZoneOffset.UTC);
    }

    private record Site(UUID id, String domain, Cookie session) {
    }
}
