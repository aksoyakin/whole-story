package world.wholestory.api.analytics;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.api.TestcontainersConfiguration;
import world.wholestory.contracts.RealtimeVisitorKeys;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who is on a site right now. The entries are seeded straight into Redis — writing them is the processor's
 * job and its own test covers it, including the part that matters most, that crawlers never get in.
 * <p>
 * What is tested here is the reading side: the window, and that it is answered to the same people the rest of
 * the dashboard is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RealtimeIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    StringRedisTemplate redis;

    @Test
    void countsOnlyTheVisitorsInsideTheWindow() throws Exception {
        Site site = registerSite();
        long now = Instant.now().getEpochSecond();
        seen(site.id(), 1L, now);
        seen(site.id(), 2L, now - 240);
        // Ten minutes ago is not "right now", however long the entry is kept around.
        seen(site.id(), 3L, now - 600);

        mvc.perform(get("/api/sites/{siteId}/stats/realtime", site.id()).cookie(site.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitors").value(2));
    }

    @Test
    void aSiteNobodyIsReadingAnswersZeroRatherThanFailing() throws Exception {
        Site site = registerSite();

        mvc.perform(get("/api/sites/{siteId}/stats/realtime", site.id()).cookie(site.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitors").value(0));
    }

    /** The same gate as every other report: somebody else's site is indistinguishable from one that is not there. */
    @Test
    void itIsAnsweredToTheSamePeopleAsTheRestOfTheDashboard() throws Exception {
        Site site = registerSite();
        seen(site.id(), 10L, Instant.now().getEpochSecond());
        Site stranger = registerSite();

        mvc.perform(get("/api/sites/{siteId}/stats/realtime", site.id()).cookie(stranger.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/sites/{siteId}/stats/realtime", site.id()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sites/{siteId}/stats/realtime", UUID.randomUUID()).cookie(site.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSharedDashboardShowsItToAnybodyAndOnlyWhenItIsShared() throws Exception {
        Site site = registerSite();
        seen(site.id(), 20L, Instant.now().getEpochSecond());

        // No cookie on either call.
        mvc.perform(get("/api/public/sites/{domain}/stats/realtime", site.domain()))
                .andExpect(status().isNotFound());

        share(site);

        mvc.perform(get("/api/public/sites/{domain}/stats/realtime", site.domain()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitors").value(1));
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    /** What the processor writes: the visitor hash, scored with the second it was last seen. */
    private void seen(UUID siteId, long visitorHash, long atEpochSecond) {
        redis.opsForZSet().add(RealtimeVisitorKeys.forSite(siteId), Long.toString(visitorHash), atEpochSecond);
    }

    private void share(Site site) throws Exception {
        mvc.perform(put("/api/sites/{siteId}/settings", site.id())
                        .cookie(site.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timezone": "UTC", "publicDashboard": true}"""))
                .andExpect(status().isOk());
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
                                {"organizationId": "%s", "domain": "%s", "timezone": "UTC"}"""
                                .formatted(organizationId, domain)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID siteId = UUID.fromString(jsonMapper.readTree(created.getResponse().getContentAsString())
                .get("siteId").asString());
        return new Site(siteId, domain, session);
    }

    private record Site(UUID id, String domain, Cookie session) {
    }
}
