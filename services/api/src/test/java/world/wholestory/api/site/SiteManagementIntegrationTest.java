package world.wholestory.api.site;

import jakarta.servlet.http.Cookie;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.api.TestcontainersConfiguration;
import world.wholestory.contracts.Topics;
import world.wholestory.contracts.TrackedDomainV1;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registering a site has to reach ingest, which is the whole point of the outbox: the publication is written in
 * the same transaction as the site and sent afterwards. These tests read the Kafka topic to prove it arrived.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SiteManagementIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    KafkaConnectionDetails kafka;

    @Test
    void registeringASiteAnnouncesTheTrackedDomain() throws Exception {
        Account account = register();
        String domain = uniqueDomain();

        MvcResult created = mvc.perform(post("/api/sites")
                        .cookie(account.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(account.organizationId(), domain, "Europe/Istanbul")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.domain").value(domain))
                .andExpect(jsonPath("$.timezone").value("Europe/Istanbul"))
                .andExpect(jsonPath("$.publicDashboard").value(false))
                .andReturn();

        UUID siteId = UUID.fromString(jsonMapper.readTree(created.getResponse().getContentAsString())
                .get("siteId").asString());

        TrackedDomainV1 announced = awaitLastMessageFor(domain);
        assertThat(announced.tracked()).isTrue();
        assertThat(announced.siteId()).isEqualTo(siteId);
        assertThat(announced.organizationId()).isEqualTo(account.organizationId());
        assertThat(announced.schemaVersion()).isEqualTo(TrackedDomainV1.SCHEMA_VERSION);
    }

    @Test
    void thePublicationIsRecordedAndThenCompleted() throws Exception {
        Account account = register();
        String domain = uniqueDomain();

        registerSite(account, domain);

        // The outbox row exists and is marked done once the message went out; a row left incomplete is retried.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.sql("""
                                select count(*) from platform.event_publication
                                where serialized_event like ? and completion_date is not null""")
                        .param("%" + domain + "%")
                        .query(Long.class).single()).isEqualTo(1L));
    }

    @Test
    void aDomainBelongsToOneSiteOnlyWhateverTheSpelling() throws Exception {
        Account account = register();
        String domain = uniqueDomain();
        registerSite(account, domain);

        mvc.perform(post("/api/sites")
                        .cookie(account.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(account.organizationId(), "WWW." + domain.toUpperCase(), null)))
                .andExpect(status().isConflict());
    }

    @Test
    void removingASiteAnnouncesThatTheDomainIsFreeAgain() throws Exception {
        Account account = register();
        String domain = uniqueDomain();
        UUID siteId = registerSite(account, domain);
        awaitLastMessageFor(domain);

        mvc.perform(delete("/api/sites/{siteId}", siteId).cookie(account.session()))
                .andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(awaitLastMessageFor(domain).tracked()).isFalse());

        // The domain is free, so somebody else may now track it.
        Account other = register();
        mvc.perform(post("/api/sites")
                        .cookie(other.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(other.organizationId(), domain, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void theOrganizationsPlanCapsTheNumberOfSites() throws Exception {
        Account account = register();
        jdbc.sql("update identity.organizations set site_limit = 1 where id = ?")
                .param(account.organizationId()).update();
        registerSite(account, uniqueDomain());

        mvc.perform(post("/api/sites")
                        .cookie(account.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(account.organizationId(), uniqueDomain(), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSiteCannotBeAddedToSomeoneElsesOrganization() throws Exception {
        Account mine = register();
        Account theirs = register();

        mvc.perform(post("/api/sites")
                        .cookie(mine.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(theirs.organizationId(), uniqueDomain(), null)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/sites").param("organizationId", theirs.organizationId().toString())
                        .cookie(mine.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheOrganizationsOwnSitesAreListed() throws Exception {
        Account account = register();
        String domain = uniqueDomain();
        registerSite(account, domain);
        registerSite(register(), uniqueDomain());

        mvc.perform(get("/api/sites").param("organizationId", account.organizationId().toString())
                        .cookie(account.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].domain").value(domain));
    }

    @Test
    void statisticsAreOnlyReadableByTheOrganizationThatOwnsTheSite() throws Exception {
        Account owner = register();
        UUID siteId = registerSite(owner, uniqueDomain());
        Account stranger = register();

        mvc.perform(statsRequest(siteId).cookie(owner.session())).andExpect(status().isOk());
        // A site that is not yours answers exactly as one that does not exist.
        mvc.perform(statsRequest(siteId).cookie(stranger.session())).andExpect(status().isNotFound());
        mvc.perform(statsRequest(UUID.randomUUID()).cookie(owner.session())).andExpect(status().isNotFound());
        mvc.perform(statsRequest(siteId)).andExpect(status().isUnauthorized());
    }

    @Test
    void aRemovedSitesStatisticsAreNoLongerReadable() throws Exception {
        Account account = register();
        UUID siteId = registerSite(account, uniqueDomain());

        mvc.perform(delete("/api/sites/{siteId}", siteId).cookie(account.session()))
                .andExpect(status().isNoContent());

        mvc.perform(statsRequest(siteId).cookie(account.session())).andExpect(status().isNotFound());
    }

    @Test
    void sitesCannotBeTouchedWithoutASession() throws Exception {
        mvc.perform(post("/api/sites").contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), uniqueDomain(), null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sites").param("organizationId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDomainThatIsNotAHostnameIsRefused() throws Exception {
        Account account = register();

        mvc.perform(post("/api/sites")
                        .cookie(account.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(account.organizationId(), "https://example.com/admin", null)))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder statsRequest(UUID siteId) {
        return get("/api/sites/{siteId}/stats/aggregate", siteId)
                .param("from", "2026-10-01")
                .param("to", "2026-10-01");
    }

    private UUID registerSite(Account account, String domain) throws Exception {
        MvcResult result = mvc.perform(post("/api/sites")
                        .cookie(account.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(account.organizationId(), domain, null)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("siteId").asString());
    }

    /** The last word the topic has about a domain, which is what a compacted log leaves behind. */
    private TrackedDomainV1 awaitLastMessageFor(String domain) {
        return await().atMost(Duration.ofSeconds(25))
                .until(() -> lastMessageFor(domain), Optional::isPresent)
                .orElseThrow();
    }

    private Optional<TrackedDomainV1> lastMessageFor(String domain) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(Topics.SITE_EVENTS));
            Optional<TrackedDomainV1> found = Optional.empty();
            for (int attempt = 0; attempt < 5; attempt++) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, byte[]> record : records) {
                    if (domain.equals(record.key())) {
                        found = Optional.of(jsonMapper.readValue(record.value(), TrackedDomainV1.class));
                    }
                }
            }
            return found;
        }
    }

    private Account register() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "name": "Owner", "password": "correct horse battery"}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID organizationId = UUID.fromString(jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("organizationId").asString());
        return new Account(result.getResponse().getCookie("SESSION"), organizationId);
    }

    private static String body(UUID organizationId, String domain, String timezone) {
        String zone = timezone == null ? "null" : "\"" + timezone + "\"";
        return """
                {"organizationId": "%s", "domain": "%s", "timezone": %s}"""
                .formatted(organizationId, domain, zone);
    }

    /** Each test registers its own account and domains: the database is shared across the class. */
    private static String uniqueDomain() {
        return "d" + UUID.randomUUID().toString().replace("-", "") + ".example.com";
    }

    private record Account(Cookie session, UUID organizationId) {
    }
}
