package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import world.wholestory.contracts.Topics;

import java.util.Map;

/**
 * Keeps the allow-list current after startup.
 * <p>
 * The group is new on every start and the listener seeks to the beginning of every partition it is given: this
 * is state each instance needs in full, not work to divide between them, so committed offsets would only be a
 * way to miss something. Re-reading the compacted log costs one record per tracked domain.
 */
@Component
@RequiredArgsConstructor
class TrackedDomainListener implements ConsumerSeekAware {

    private final TrackedDomainUpdates updates;

    @KafkaListener(topics = Topics.SITE_EVENTS, groupId = "ingest-sites-${random.uuid}")
    void onSiteEvent(ConsumerRecord<String, byte[]> record) {
        updates.apply(record);
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToBeginning(assignments.keySet());
    }
}
