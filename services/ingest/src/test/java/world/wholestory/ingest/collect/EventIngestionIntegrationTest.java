package world.wholestory.ingest.collect;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.Topics;
import world.wholestory.ingest.TestcontainersConfiguration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ingest.geoip-database=src/test/resources/geoip/GeoIP2-City-Test.mmdb")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventIngestionIntegrationTest {

    /** In MaxMind's test database: Boxford, England. */
    private static final String CLIENT_IP = "2.125.160.216";

    @Autowired
    MockMvc mvc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    KafkaConnectionDetails kafkaConnection;
    @Autowired
    JsonMapper jsonMapper;

    @Test
    void publishesAPrivacySafeEventForARegisteredDomain() throws Exception {
        UUID siteId = UUID.randomUUID();
        redis.opsForHash().put(SiteRegistry.KEY, "example.com", siteId.toString());

        mvc.perform(post("/api/event")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("User-Agent", "Mozilla/5.0 (Macintosh)")
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .content("""
                                {"name":"pageview","url":"https://www.example.com/pricing?utm_source=newsletter","domain":"www.example.com","referrer":"https://news.ycombinator.com/"}
                                """))
                .andExpect(status().isAccepted());

        ConsumerRecord<String, byte[]> record = consumeFor(siteId);
        String json = new String(record.value(), StandardCharsets.UTF_8);
        RawEventV1 event = jsonMapper.readValue(record.value(), RawEventV1.class);

        assertThat(json).doesNotContain(CLIENT_IP).doesNotContain("\"pageview\":true");
        assertThat(record.key()).isEqualTo(siteId + ":" + event.visitorHash());
        assertThat(event.siteId()).isEqualTo(siteId);
        assertThat(event.eventId().version()).isEqualTo(7);
        assertThat(event.hostname()).isEqualTo("www.example.com");
        assertThat(event.pathname()).isEqualTo("/pricing");
        assertThat(event.utmSource()).isEqualTo("newsletter");
        assertThat(event.referrer()).isEqualTo("https://news.ycombinator.com/");
        // The location survives, the address it came from does not.
        assertThat(event.countryCode()).isEqualTo("GB");
        assertThat(event.subdivisionCode()).isEqualTo("GB-ENG");
        assertThat(event.cityGeonameId()).isEqualTo(2655045);
    }

    @Test
    void acceptsAnOriginThatMatchesTheTrackedDomain() throws Exception {
        UUID siteId = UUID.randomUUID();
        redis.opsForHash().put(SiteRegistry.KEY, "origin-ok.test", siteId.toString());

        mvc.perform(event("origin-ok.test").header("Origin", "https://www.origin-ok.test"))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsAnOriginFromAnotherSite() throws Exception {
        UUID siteId = UUID.randomUUID();
        redis.opsForHash().put(SiteRegistry.KEY, "origin-bad.test", siteId.toString());

        // A page on evil.test must not be able to report events for someone else's site.
        mvc.perform(event("origin-bad.test").header("Origin", "https://evil.test"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsAMissingOriginBecauseBrowsersDoNotAlwaysSendIt() throws Exception {
        UUID siteId = UUID.randomUUID();
        redis.opsForHash().put(SiteRegistry.KEY, "origin-absent.test", siteId.toString());

        mvc.perform(event("origin-absent.test")).andExpect(status().isAccepted());
    }

    @Test
    void rejectsAVisitorThatSendsTooManyEventsInAMinute() throws Exception {
        UUID siteId = UUID.randomUUID();
        redis.opsForHash().put(SiteRegistry.KEY, "flood.test", siteId.toString());

        for (int i = 0; i < 60; i++) {
            mvc.perform(event("flood.test")).andExpect(status().isAccepted());
        }
        mvc.perform(event("flood.test")).andExpect(status().isTooManyRequests());
    }

    @Test
    void rejectsUnknownDomains() throws Exception {
        mvc.perform(post("/api/event")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("""
                                {"name":"pageview","url":"https://unknown.test/","domain":"unknown.test"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedPayloads() throws Exception {
        mvc.perform(post("/api/event").contentType(MediaType.TEXT_PLAIN).content("not json"))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder event(String domain) {
        return post("/api/event")
                .contentType(MediaType.TEXT_PLAIN)
                .header("User-Agent", "Mozilla/5.0 (Macintosh)")
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .content("{\"name\":\"pageview\",\"url\":\"https://" + domain + "/\",\"domain\":\"" + domain + "\"}");
    }

    /** Other tests in this class publish to the same topic, so the record has to be matched by site. */
    private ConsumerRecord<String, byte[]> consumeFor(UUID siteId) {
        Map<String, Object> config = Map.of(
                "bootstrap.servers", String.join(",", kafkaConnection.getBootstrapServers()),
                "group.id", "ingest-test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(Topics.RAW_EVENTS));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key() != null && record.key().startsWith(siteId + ":")) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No event for site " + siteId + " published to " + Topics.RAW_EVENTS);
    }
}
