package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.TrackedDomainV1;

/**
 * Applies one record of the {@code site-events} topic to the local allow-list. Used by both the load at startup
 * and the listener that follows afterwards, so a rebuilt list and a live update cannot drift apart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TrackedDomainUpdates {

    private final JsonMapper jsonMapper;
    private final SiteRegistry sites;

    void apply(ConsumerRecord<String, byte[]> record) {
        TrackedDomainV1 update;
        try {
            update = jsonMapper.readValue(record.value(), TrackedDomainV1.class);
        } catch (JacksonException e) {
            // Retrying would never succeed and would block every later record about every other domain.
            log.warn("Skipping malformed site event at {}-{}@{}", record.topic(), record.partition(), record.offset(), e);
            return;
        }
        if (update.schemaVersion() != TrackedDomainV1.SCHEMA_VERSION) {
            log.warn("Skipping site event with unsupported schema version {}", update.schemaVersion());
            return;
        }
        if (update.tracked()) {
            sites.track(update.domain(), update.siteId());
        } else {
            sites.forget(update.domain());
        }
    }
}
