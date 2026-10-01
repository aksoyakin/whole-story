package world.wholestory.ingest.collect;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.Topics;
import world.wholestory.contracts.TrackedDomainV1;
import world.wholestory.ingest.TestcontainersConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ingest learns which domains are tracked from the compacted {@code site-events} topic, which Site Management
 * writes through its outbox. Nothing registers a domain by hand any more.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TrackedDomainIntegrationTest {

    private static final String CHROME = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    @Autowired
    MockMvc mvc;
    @Autowired
    KafkaConnectionDetails kafka;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    TrackedDomainLoader loader;

    @Test
    void aDomainIsAcceptedOnceSiteManagementAnnouncesIt() throws Exception {
        String domain = uniqueDomain();
        UUID siteId = UUID.randomUUID();

        mvc.perform(event(domain)).andExpect(status().isBadRequest());

        announce(TrackedDomainV1.tracked(domain, siteId, UUID.randomUUID(), Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(event(domain)).andExpect(status().isAccepted()));
    }

    @Test
    void aRemovedSiteStopsBeingAccepted() throws Exception {
        String domain = uniqueDomain();
        UUID siteId = UUID.randomUUID();
        announce(TrackedDomainV1.tracked(domain, siteId, UUID.randomUUID(), Instant.now()));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(event(domain)).andExpect(status().isAccepted()));

        announce(TrackedDomainV1.untracked(domain, siteId, Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(event(domain)).andExpect(status().isBadRequest()));
    }

    /** The announcement carries the normalised domain, and ingest normalises what the tracker sends. */
    @Test
    void theDomainIsMatchedWithoutItsWwwPrefixOrCapitalisation() throws Exception {
        String domain = uniqueDomain();
        announce(TrackedDomainV1.tracked(domain, UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(event("WWW." + domain.toUpperCase())).andExpect(status().isAccepted()));
    }

    /**
     * Redis is a cache, not the source of truth: losing it must not silently stop every site from being counted.
     * This is what the load at startup does, run here against a topic that already holds the announcements.
     */
    @Test
    void theWholeListIsRebuiltFromTheTopicWhenRedisIsEmpty() throws Exception {
        String domain = uniqueDomain();
        announce(TrackedDomainV1.tracked(domain, UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                mvc.perform(event(domain)).andExpect(status().isAccepted()));

        redis.delete(SiteRegistry.KEY);
        mvc.perform(event(domain)).andExpect(status().isBadRequest());

        loader.afterPropertiesSet();

        mvc.perform(event(domain)).andExpect(status().isAccepted());
    }

    private void announce(TrackedDomainV1 update) {
        Map<String, Object> config = Map.of("bootstrap.servers", String.join(",", kafka.getBootstrapServers()));
        try (var producer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(
                    Topics.SITE_EVENTS, update.domain(), jsonMapper.writeValueAsBytes(update)));
            producer.flush();
        }
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder event(String domain) {
        return post("/api/event")
                .contentType(MediaType.TEXT_PLAIN)
                .header("User-Agent", CHROME)
                .content("""
                        {"name": "pageview", "url": "https://%s/", "domain": "%s"}"""
                        .formatted(domain, domain));
    }

    private static String uniqueDomain() {
        return "d" + UUID.randomUUID().toString().replace("-", "") + ".example.com";
    }
}
