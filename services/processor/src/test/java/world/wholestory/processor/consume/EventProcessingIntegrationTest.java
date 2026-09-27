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
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.RawEventV1;
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

    @Autowired
    KafkaConnectionDetails kafka;
    @Autowired
    JdbcClient jdbc;
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
                        select count(*) over () as sessions, pageviews, events, entry_page, exit_page, is_bounce, duration_seconds
                        from analytics.sessions where site_id = ?""")
                .param(siteId).query().singleRow();
        assertThat(session)
                .containsEntry("sessions", 1L)
                .containsEntry("pageviews", 2)
                .containsEntry("events", 3)
                .containsEntry("entry_page", "/")
                .containsEntry("exit_page", "/pricing")
                .containsEntry("is_bounce", false)
                .containsEntry("duration_seconds", 40);

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
        return new RawEventV1(RawEventV1.SCHEMA_VERSION, UuidV7.generate(at), at, siteId, 42L, null, name,
                "example.com", path, null, null, null, null, null, null, null, null, null, "UA", null);
    }
}
