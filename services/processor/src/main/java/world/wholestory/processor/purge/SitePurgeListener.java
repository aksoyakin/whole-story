package world.wholestory.processor.purge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.SitePurgeV1;
import world.wholestory.contracts.Topics;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hears that a site was removed and deletes what was collected for it.
 * <p>
 * Unlike ingest's reader of {@code site-events}, this one commits its offsets and keeps them: the purge is work
 * to be done once, not state to be rebuilt, so there is nothing to gain from re-reading the log on every start.
 * Offsets that are lost anyway only cost a second pass over sites that are already empty.
 * <p>
 * The first deployment of this listener starts at the beginning of the topic, so the sites removed before it
 * existed are cleaned up as well.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SitePurgeListener {

    private final JsonMapper jsonMapper;
    private final SiteDataPurger purger;

    @KafkaListener(topics = Topics.SITE_PURGE)
    void onBatch(List<ConsumerRecord<String, byte[]>> records) {
        // One site is purged once per batch however many records name it; the work is the same either way.
        Set<UUID> sites = new LinkedHashSet<>();
        for (ConsumerRecord<String, byte[]> record : records) {
            decode(record).ifPresent(purge -> sites.add(purge.siteId()));
        }
        sites.forEach(purger::purge);
    }

    private Optional<SitePurgeV1> decode(ConsumerRecord<String, byte[]> record) {
        try {
            SitePurgeV1 purge = jsonMapper.readValue(record.value(), SitePurgeV1.class);
            if (purge.schemaVersion() != SitePurgeV1.SCHEMA_VERSION) {
                log.warn("Skipping purge record with unsupported schema version {}", purge.schemaVersion());
                return Optional.empty();
            }
            return Optional.of(purge);
        } catch (JacksonException e) {
            // Retrying could never succeed and would hold up every later purge on the partition.
            log.warn("Skipping malformed purge record at {}-{}@{}",
                    record.topic(), record.partition(), record.offset(), e);
            return Optional.empty();
        }
    }
}
