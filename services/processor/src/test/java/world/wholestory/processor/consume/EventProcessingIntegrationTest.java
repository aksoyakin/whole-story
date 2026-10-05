package world.wholestory.processor.consume;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.RealtimeVisitorKeys;
import world.wholestory.contracts.Topics;
import world.wholestory.contracts.UuidV7;
import world.wholestory.processor.TestcontainersConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventProcessingIntegrationTest {

    private static final String CHROME_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    private static final String GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    @Autowired
    KafkaConnectionDetails kafka;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    JsonMapper jsonMapper;

    @Test
    void storesEventsSessionsAndRollupsExactlyOnceEvenWhenABatchIsReplayed() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        RawEventV1 landing = event(siteId, "pageview", "/", now);
        RawEventV1 pricing = event(siteId, "pageview", "/pricing", now.plusSeconds(20));
        RawEventV1 signup = event(siteId, "Signup", "/pricing", now.plusSeconds(40));

        send(List.of(landing, pricing, signup));
        send(List.of(pricing, signup)); // at-least-once delivery: the same events arrive again

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(3));
        // give the replayed batch time to be (not) applied
        await().pollDelay(Duration.ofSeconds(2)).until(() -> true);

        Map<String, Object> session = jdbc.sql("""
                        select count(*) over () as sessions, pageviews, events, entry_page, exit_page, is_bounce,
                               duration_seconds, browser, os, device_type
                        from analytics.api_sessions where site_id = ?""")
                .param(siteId).query().singleRow();
        assertThat(session)
                .containsEntry("sessions", 1L)
                .containsEntry("pageviews", 2)
                .containsEntry("events", 3)
                .containsEntry("entry_page", "/")
                .containsEntry("exit_page", "/pricing")
                .containsEntry("is_bounce", false)
                .containsEntry("duration_seconds", 40);

        assertThat(session)
                .containsEntry("browser", "Chrome")
                .containsEntry("os", "Mac OS")
                .containsEntry("device_type", "desktop");

        assertThat(count("select coalesce(sum(pageviews), 0) from analytics.page_hourly where site_id = ?", siteId))
                .isEqualTo(2);
        assertThat(count("select coalesce(sum(count), 0) from analytics.custom_event_hourly where site_id = ?", siteId))
                .isEqualTo(1);
    }

    @Test
    void continuesASessionAcrossBatches() {
        UUID siteId = UUID.randomUUID();
        // sub-millisecond precision, as produced by ingest's clock
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(263_073_000);

        send(List.of(event(siteId, "pageview", "/", now)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(1));
        send(List.of(event(siteId, "pageview", "/docs", now.plusSeconds(5))));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(2));

        assertThat(count("select count(*) from analytics.sessions where site_id = ?", siteId)).isEqualTo(1);
        assertThat(count("select sum(pageviews) from analytics.sessions where site_id = ?", siteId)).isEqualTo(2);
    }

    @Test
    void aSessionBouncesOnlyWhenTheVisitorDidNothingMeaningful() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        send(List.of(
                event(siteId, "pageview", "/", now, CHROME_MAC, 10L),
                event(siteId, "pageview", "/", now, CHROME_MAC, 11L),
                event(siteId, "Signup", "/", now.plusSeconds(10), CHROME_MAC, 11L),
                // A visit can start with a custom event: the tracker flushes queued calls before the pageview.
                event(siteId, "Signup", "/", now, CHROME_MAC, 12L)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(4));

        assertThat(bounced(siteId, 10L)).as("a single pageview and nothing else").isTrue();
        assertThat(bounced(siteId, 11L)).as("one pageview plus a custom event").isFalse();
        assertThat(bounced(siteId, 12L)).as("a custom event without a pageview").isFalse();
    }

    /** What ingest resolved has to survive the whole pipeline, or the dashboard has codes and no labels. */
    @Test
    void storesThePlaceNamesIngestResolved() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        RawEventV1 fromBoxford = new RawEventV1(RawEventV1.SCHEMA_VERSION, UuidV7.generate(now), now, siteId, 13L,
                null, "pageview", "example.com", "/", null, null, null, null, null, null,
                "GB", "GB-ENG", "England", 2655045, "Boxford", CHROME_MAC, null);
        send(List.of(fromBoxford));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(1));

        Map<String, Object> event = jdbc.sql("select country_code, subdivision_code, subdivision_name, "
                        + "city_geoname_id, city_name from analytics.api_events where site_id = ?")
                .param(siteId).query().singleRow();
        assertThat(event)
                .containsEntry("country_code", "GB")
                .containsEntry("subdivision_code", "GB-ENG")
                .containsEntry("subdivision_name", "England")
                .containsEntry("city_geoname_id", 2655045)
                .containsEntry("city_name", "Boxford");

        Map<String, Object> session = jdbc.sql("select country_code, subdivision_name, city_name "
                        + "from analytics.api_sessions where site_id = ?")
                .param(siteId).query().singleRow();
        assertThat(session)
                .containsEntry("country_code", "GB")
                .containsEntry("subdivision_name", "England")
                .containsEntry("city_name", "Boxford");
    }

    @Test
    void neverStoresBotTraffic() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // One partition in tests, so the bot is processed before the visitor that follows it.
        send(List.of(
                event(siteId, "pageview", "/", now, GOOGLEBOT, 7L),
                event(siteId, "pageview", "/", now.plusSeconds(1), CHROME_MAC, 8L)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(1));

        assertThat(jdbc.sql("select browser from analytics.events where site_id = ?").param(siteId)
                .query(String.class).single()).isEqualTo("Chrome");
        assertThat(count("select count(*) from analytics.sessions where site_id = ?", siteId)).isEqualTo(1);
        assertThat(count("select coalesce(sum(pageviews), 0) from analytics.page_hourly where site_id = ?", siteId))
                .isEqualTo(1);
    }

    @Test
    void storesTheTrafficSourceOfAVisit() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        RawEventV1 fromGoogle = new RawEventV1(RawEventV1.SCHEMA_VERSION, UuidV7.generate(now), now, siteId, 9L, null,
                "pageview", "example.com", "/", "https://www.google.de/", null, null, null, null, null, null, null,
                null, null, null, CHROME_MAC, null);
        send(List.of(fromGoogle));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(1));

        assertThat(jdbc.sql("select referrer_source from analytics.events where site_id = ?").param(siteId)
                .query(String.class).single()).isEqualTo("Google");
        assertThat(jdbc.sql("select referrer_source from analytics.sessions where site_id = ?").param(siteId)
                .query(String.class).single()).isEqualTo("Google");
    }

    /**
     * The reason this is counted here and not in ingest: the bot filter has run by now. Counted there, the most
     * visible number on the dashboard would include every crawler.
     */
    @Test
    void countsWhoIsOnTheSiteRightNowAndNotTheCrawlers() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        send(List.of(
                event(siteId, "pageview", "/", now, GOOGLEBOT, 70L),
                event(siteId, "pageview", "/", now.plusSeconds(1), CHROME_MAC, 71L),
                // The same visitor again is the same person, not a second one.
                event(siteId, "pageview", "/docs", now.plusSeconds(2), CHROME_MAC, 71L),
                event(siteId, "pageview", "/", now.plusSeconds(3), CHROME_MAC, 72L)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(onSiteNow(siteId)).isEqualTo(2L));
    }

    /**
     * The regression the load test found. Re-reading the topic used to put every visitor in history back into
     * this set, because the recorder was handed the whole batch and scored each member with the time it ran;
     * a rewound consumer group took a counter that had been cleared to zero up to 80,000. The score is the
     * event's own time now, so an event from outside the window is trimmed the moment it is written.
     */
    @Test
    void doesNotCountVisitorsWhoseEventsAreTooOldToBeHereNow() {
        UUID siteId = UUID.randomUUID();
        Instant longAgo = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofMinutes(20));

        send(List.of(
                event(siteId, "pageview", "/", longAgo, CHROME_MAC, 80L),
                event(siteId, "pageview", "/docs", longAgo.plusSeconds(5), CHROME_MAC, 81L)));

        // The rows arrive either way: an event from twenty minutes ago is stored like any other.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(2));
        assertThat(onSiteNow(siteId)).isZero();
    }

    /**
     * The other half of the same fix. Scoring by event time is only safe if a score cannot move backwards:
     * otherwise an old event arriving late would drag a visitor who really is here into the past, and the
     * trim that follows would remove somebody who is present. {@code ZADD GT} is what prevents it.
     */
    @Test
    void aLateEventDoesNotRemoveAVisitorWhoIsHereNow() {
        UUID siteId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        send(List.of(event(siteId, "pageview", "/", now, CHROME_MAC, 90L)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(onSiteNow(siteId)).isEqualTo(1L));

        // The same visitor, an event from twenty minutes ago that only reaches us now.
        send(List.of(event(siteId, "pageview", "/docs", now.minus(Duration.ofMinutes(20)), CHROME_MAC, 90L)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("select count(*) from analytics.events where site_id = ?", siteId)).isEqualTo(2));
        assertThat(onSiteNow(siteId)).isEqualTo(1L);
    }

    private long onSiteNow(UUID siteId) {
        Long counted = redis.opsForZSet()
                .count(RealtimeVisitorKeys.forSite(siteId), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return counted == null ? 0L : counted;
    }

    @Test
    void apiRoleCanReadTheContractViewsButNotTheTables() {
        List<String> tables = jdbc.sql("""
                        select table_name from information_schema.role_table_grants
                        where grantee = 'wholestory_api' and table_schema = 'analytics' and privilege_type = 'SELECT'
                        order by table_name""")
                .query(String.class).list();

        assertThat(tables).containsExactly("api_custom_event_hourly", "api_events", "api_page_hourly", "api_sessions");
    }

    private long count(String sql, UUID siteId) {
        return jdbc.sql(sql).param(siteId).query(Long.class).single();
    }

    /** Read through the contract view, which is where the bounce definition lives (ADR 0017). */
    private boolean bounced(UUID siteId, long visitorHash) {
        return jdbc.sql("select is_bounce from analytics.api_sessions where site_id = ? and visitor_hash = ?")
                .params(siteId, visitorHash)
                .query(Boolean.class)
                .single();
    }

    private void send(List<RawEventV1> events) {
        Map<String, Object> config = Map.of("bootstrap.servers", String.join(",", kafka.getBootstrapServers()));
        try (var producer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer())) {
            for (RawEventV1 event : events) {
                producer.send(new ProducerRecord<>(Topics.RAW_EVENTS, event.partitionKey(), jsonMapper.writeValueAsBytes(event)));
            }
            producer.flush();
        }
    }

    private static RawEventV1 event(UUID siteId, String name, String path, Instant at) {
        return event(siteId, name, path, at, CHROME_MAC, 42L);
    }

    private static RawEventV1 event(UUID siteId, String name, String path, Instant at, String userAgent, long visitor) {
        return new RawEventV1(RawEventV1.SCHEMA_VERSION, UuidV7.generate(at), at, siteId, visitor, null, name,
                "example.com", path, null, null, null, null, null, null, null, null, null, null, null, userAgent, null);
    }
}
