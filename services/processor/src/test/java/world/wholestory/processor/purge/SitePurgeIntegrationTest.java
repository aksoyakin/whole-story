package world.wholestory.processor.purge;

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
import world.wholestory.contracts.SitePurgeV1;
import world.wholestory.contracts.Topics;
import world.wholestory.contracts.UuidV7;
import world.wholestory.processor.TestcontainersConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Removing a site has to take its data with it, which is a promise the product makes and nothing kept until
 * now (D-034). The deletion runs here because the processor owns the analytics schema; api, which knows the
 * site was removed, has no privilege to delete a row from it.
 * <p>
 * Rows are seeded at the current instant so that they land in the partitions {@code PartitionMaintenance}
 * creates at startup.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SitePurgeIntegrationTest {

    @Autowired
    KafkaConnectionDetails kafka;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    JsonMapper jsonMapper;

    @Test
    void takesEverythingTheRemovedSiteHadAndTouchesNobodyElse() {
        UUID removed = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        seedEverything(removed);
        seedEverything(kept);

        requestPurge(removed);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(rowsFor(removed)).isZero());
        // The neighbour is the point of the test: a purge keyed on one site must not reach across to another.
        assertThat(rowsFor(kept)).isEqualTo(4);
    }

    /** The message can arrive more than once (ADR 0006), and replaying the compacted topic repeats it too. */
    @Test
    void purgingTwiceIsHarmless() {
        UUID removed = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        seedEverything(removed);
        seedEverything(kept);

        requestPurge(removed);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(rowsFor(removed)).isZero());

        requestPurge(removed);

        await().pollDelay(Duration.ofSeconds(2)).until(() -> true);
        assertThat(rowsFor(removed)).isZero();
        assertThat(rowsFor(kept)).isEqualTo(4);
    }

    /** A site that never collected anything is the ordinary case for somebody who gave up on the snippet. */
    @Test
    void purgingASiteWithNoDataDoesNotFail() {
        UUID empty = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        seedEverything(kept);

        requestPurge(empty);

        await().pollDelay(Duration.ofSeconds(3)).until(() -> true);
        assertThat(rowsFor(empty)).isZero();
        // Proof the listener kept working rather than dying on the empty one.
        requestPurge(kept);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(rowsFor(kept)).isZero());
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    /** One row in each of the four tables that carry a site id. */
    private void seedEverything(UUID siteId) {
        Instant now = Instant.now();
        UUID sessionId = UuidV7.generate(now);
        jdbc.sql("""
                        insert into analytics.events
                            (event_id, timestamp, site_id, session_id, visitor_hash, name, hostname, pathname)
                        values (?, ?, ?, ?, 1, 'pageview', 'example.com', '/')""")
                .params(UuidV7.generate(now), at(now), siteId, sessionId)
                .update();
        jdbc.sql("""
                        insert into analytics.sessions
                            (session_id, started_at, site_id, visitor_hash, ended_at, pageviews, events,
                             entry_page, exit_page)
                        values (?, ?, ?, 1, ?, 1, 1, '/', '/')""")
                .params(sessionId, at(now), siteId, at(now))
                .update();
        jdbc.sql("insert into analytics.page_hourly (site_id, hour, pathname, pageviews) values (?, ?, '/', 1)")
                .params(siteId, at(now.truncatedTo(ChronoUnit.HOURS)))
                .update();
        jdbc.sql("""
                        insert into analytics.custom_event_hourly (site_id, hour, event_name, count)
                        values (?, ?, 'Signup', 1)""")
                .params(siteId, at(now.truncatedTo(ChronoUnit.HOURS)))
                .update();
    }

    private long rowsFor(UUID siteId) {
        return count("analytics.events", siteId)
                + count("analytics.sessions", siteId)
                + count("analytics.page_hourly", siteId)
                + count("analytics.custom_event_hourly", siteId);
    }

    private long count(String table, UUID siteId) {
        return jdbc.sql("select count(*) from " + table + " where site_id = ?")
                .param(siteId)
                .query(Long.class)
                .single();
    }

    private void requestPurge(UUID siteId) {
        SitePurgeV1 purge = SitePurgeV1.of(siteId, Instant.now());
        Map<String, Object> config = Map.of("bootstrap.servers", String.join(",", kafka.getBootstrapServers()));
        try (var producer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(
                    Topics.SITE_PURGE, siteId.toString(), jsonMapper.writeValueAsBytes(purge)));
            producer.flush();
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
