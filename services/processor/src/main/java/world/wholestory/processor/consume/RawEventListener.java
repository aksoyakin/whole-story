package world.wholestory.processor.consume;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.Topics;
import world.wholestory.processor.enrichment.EventEnricher;
import world.wholestory.processor.persistence.BatchPersister;
import world.wholestory.processor.realtime.RealtimeVisitorRecorder;
import world.wholestory.processor.sessionization.SessionizedEvent;
import world.wholestory.processor.sessionization.Sessionizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Offsets are committed only after the batch listener returns, i.e. after the database transaction committed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RawEventListener {

    private final JsonMapper jsonMapper;
    private final EventEnricher enricher;
    private final Sessionizer sessionizer;
    private final BatchPersister persister;
    private final RealtimeVisitorRecorder realtimeVisitors;

    @KafkaListener(topics = Topics.RAW_EVENTS)
    void onBatch(List<ConsumerRecord<String, byte[]>> records) {
        List<RawEventV1> events = new ArrayList<>(records.size());
        for (ConsumerRecord<String, byte[]> record : records) {
            decode(record, events);
        }
        List<SessionizedEvent> sessionized = sessionizer.sessionize(enricher.enrich(events));
        List<SessionizedEvent> stored = persister.persist(sessionized);
        // Outside the database transaction on purpose: this is a number that expires by itself, and it must
        // not be able to fail a batch that was written correctly.
        //
        // Given what was stored rather than what arrived, which is D-032's rule and was missing here: a batch
        // that had all been seen before is a batch that happened in the past, and feeding it to the counter
        // put the visitors of history back onto the page. Measured by the load test — rewinding the consumer
        // group took a cleared counter to 80,000.
        realtimeVisitors.record(stored);
        log.debug("Batch of {} records, {} new events stored", records.size(), stored.size());
    }

    private void decode(ConsumerRecord<String, byte[]> record, List<RawEventV1> into) {
        try {
            RawEventV1 event = jsonMapper.readValue(record.value(), RawEventV1.class);
            if (event.schemaVersion() != RawEventV1.SCHEMA_VERSION) {
                log.warn("Skipping record at {}-{}@{}: unsupported schema version {}",
                        record.topic(), record.partition(), record.offset(), event.schemaVersion());
                return;
            }
            into.add(event);
        } catch (JacksonException e) {
            // A malformed record can never succeed; retrying it would block the partition forever.
            log.warn("Skipping malformed record at {}-{}@{}", record.topic(), record.partition(), record.offset(), e);
        }
    }
}
