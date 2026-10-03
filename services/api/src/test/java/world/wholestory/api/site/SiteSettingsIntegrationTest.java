package world.wholestory.api.site;

import jakarta.servlet.http.Cookie;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
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
import world.wholestory.contracts.Topics;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a site owner may change about their own site, and what that is allowed to affect.
 * <p>
 * The two settings are deliberately inert in the pipeline: neither changes a single stored row. The timezone
 * only moves where a day is cut when the events are read back (D-020), which is why the proof below is a
 * reporting one, and sharing only decides who may read them. Nothing is announced to ingest for either.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SiteSettingsIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    PostgreSQLContainer postgres;
    @Autowired
    KafkaConnectionDetails kafka;

    /** Seeds the analytics tables; api's own role may only read the views (D-037). */
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
    void theSettingsComeBackAsTheSiteNowStands() throws Exception {
        Site site = registerSite("UTC");

        mvc.perform(settings(site, "Europe/Istanbul", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteId").value(site.id().toString()))
                .andExpect(jsonPath("$.timezone").value("Europe/Istanbul"))
                .andExpect(jsonPath("$.publicDashboard").value(true));

        // And the list agrees, so the change really was stored rather than only echoed back.
        mvc.perform(get("/api/sites").param("organizationId", site.organizationId().toString())
                        .cookie(site.session()))
                .andExpect(jsonPath("$[0].timezone").value("Europe/Istanbul"))
                .andExpect(jsonPath("$[0].publicDashboard").value(true));
    }

    /**
     * The point of the setting. One visit at 22:00 UTC belongs to 30 September in UTC and to 1 October in
     * Istanbul, and moving the site moves it — without a single stored row changing.
     */
    @Test
    void movingTheTimezoneMovesWhichDayAVisitBelongsTo() throws Exception {
        Site site = registerSite("UTC");
        session(site.id(), 1L, "2026-09-30T22:00:00Z");

        mvc.perform(summary(site, "2026-09-30")).andExpect(jsonPath("$.visitors").value(1));
        mvc.perform(summary(site, "2026-10-01")).andExpect(jsonPath("$.visitors").value(0));

        mvc.perform(settings(site, "Europe/Istanbul", false)).andExpect(status().isOk());

        mvc.perform(summary(site, "2026-09-30")).andExpect(jsonPath("$.visitors").value(0));
        mvc.perform(summary(site, "2026-10-01")).andExpect(jsonPath("$.visitors").value(1));
    }

    /**
     * Ingest reads every message on the topic as the current truth about a domain. Neither setting is any of its
     * business, so changing them must leave the registration as the last and only word about this domain.
     */
    @Test
    void changingSettingsTellsIngestNothing() throws Exception {
        Site site = registerSite("UTC");
        await().atMost(Duration.ofSeconds(25)).until(() -> recordsFor(site.domain()).size() == 1);

        mvc.perform(settings(site, "Europe/Istanbul", true)).andExpect(status().isOk());
        mvc.perform(settings(site, "Asia/Kolkata", false)).andExpect(status().isOk());

        // Long enough that a publication the outbox was about to send would have arrived.
        await().pollDelay(Duration.ofSeconds(2)).until(() -> true);
        assertThat(recordsFor(site.domain())).hasSize(1);
    }

    @Test
    void aZoneTheJdkDoesNotKnowIsRefused() throws Exception {
        Site site = registerSite("UTC");

        mvc.perform(settings(site, "Europe/Atlantis", false)).andExpect(status().isBadRequest());
        mvc.perform(settings(site, "", false)).andExpect(status().isBadRequest());

        mvc.perform(get("/api/sites").param("organizationId", site.organizationId().toString())
                        .cookie(site.session()))
                .andExpect(jsonPath("$[0].timezone").value("UTC"));
    }

    /** Omitting the flag would otherwise read as false and quietly close a dashboard somebody was sharing. */
    @Test
    void leavingTheSharingFlagOutIsRefusedRatherThanReadAsFalse() throws Exception {
        Site site = registerSite("UTC");
        mvc.perform(settings(site, "UTC", true)).andExpect(status().isOk());

        mvc.perform(put("/api/sites/{siteId}/settings", site.id())
                        .cookie(site.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timezone": "UTC"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/sites").param("organizationId", site.organizationId().toString())
                        .cookie(site.session()))
                .andExpect(jsonPath("$[0].publicDashboard").value(true));
    }

    @Test
    void onlyTheOrganizationThatOwnsTheSiteMayChangeIt() throws Exception {
        Site site = registerSite("UTC");
        Site stranger = registerSite("UTC");

        mvc.perform(settingsAs(site, stranger.session(), "Asia/Kolkata", true))
                .andExpect(status().isNotFound());
        mvc.perform(settingsAs(site, null, "Asia/Kolkata", true))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/sites").param("organizationId", site.organizationId().toString())
                        .cookie(site.session()))
                .andExpect(jsonPath("$[0].timezone").value("UTC"));
    }

    @Test
    void aRemovedSiteHasNoSettingsLeftToChange() throws Exception {
        Site site = registerSite("UTC");
        mvc.perform(delete("/api/sites/{siteId}", site.id()).cookie(site.session()))
                .andExpect(status().isNoContent());

        mvc.perform(settings(site, "Europe/Istanbul", true)).andExpect(status().isNotFound());
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder settings(Site site, String timezone, boolean publicDashboard) {
        return settingsAs(site, site.session(), timezone, publicDashboard);
    }

    /** Built whole every time: MockMvc's cookie() appends rather than replaces. */
    private MockHttpServletRequestBuilder settingsAs(Site site, Cookie session, String timezone,
                                                     boolean publicDashboard) {
        MockHttpServletRequestBuilder request = put("/api/sites/{siteId}/settings", site.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"timezone": "%s", "publicDashboard": %s}""".formatted(timezone, publicDashboard));
        return session == null ? request : request.cookie(session);
    }

    private MockHttpServletRequestBuilder summary(Site site, String day) {
        return get("/api/sites/{siteId}/stats/summary", site.id())
                .param("from", day)
                .param("to", day)
                .cookie(site.session());
    }

    private void session(UUID siteId, long visitor, String startedAt) {
        seed.sql("""
                        insert into analytics.sessions
                            (session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                             entry_page, exit_page)
                        values (?, ?, ?, ?, ?, 1, 1, '/', '/')""")
                .params(UUID.randomUUID(), at(startedAt), siteId, visitor, at(startedAt))
                .update();
    }

    /** Every record the topic holds for this domain; one means the registration and nothing since. */
    private List<ConsumerRecord<String, byte[]>> recordsFor(String domain) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, byte[]>> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(Topics.SITE_EVENTS));
            for (int attempt = 0; attempt < 5; attempt++) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, byte[]> record : records) {
                    if (domain.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }

    private Site registerSite(String zone) throws Exception {
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
                                .formatted(organizationId, domain, zone)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID siteId = UUID.fromString(jsonMapper.readTree(created.getResponse().getContentAsString())
                .get("siteId").asString());
        return new Site(siteId, domain, session, organizationId);
    }

    private static OffsetDateTime at(String instant) {
        return Instant.parse(instant).atOffset(ZoneOffset.UTC);
    }

    private record Site(UUID id, String domain, Cookie session, UUID organizationId) {
    }
}
