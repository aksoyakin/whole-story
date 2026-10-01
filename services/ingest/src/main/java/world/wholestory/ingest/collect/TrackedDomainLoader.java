package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;
import world.wholestory.contracts.Topics;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Reads the whole compacted {@code site-events} topic before ingest starts serving.
 * <p>
 * This runs during startup on purpose: an instance that began accepting events with a half-built allow-list
 * would answer {@code 400 unknown domain} to real visitors, and that loss is invisible — the tracker does not
 * retry. Blocking here means the container simply is not ready yet, and the reverse proxy sends it no traffic.
 * <p>
 * Reading from the beginning is what makes the topic's compaction useful: whatever Redis held before, the list is
 * rebuilt from the log. Applying a record twice changes nothing, so the listener that follows may read the log
 * again without any coordination.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TrackedDomainLoader implements InitializingBean {

    private static final Duration POLL = Duration.ofSeconds(1);

    private final ConsumerFactory<String, byte[]> consumers;
    private final TrackedDomainUpdates updates;
    private final SiteRegistry sites;

    @Override
    public void afterPropertiesSet() {
        try (Consumer<String, byte[]> consumer = consumers.createConsumer("ingest-site-load", null)) {
            List<TopicPartition> partitions = partitionsOf(consumer);
            if (partitions.isEmpty()) {
                // Only possible before Site Management has ever published, i.e. when there are no sites at all.
                log.warn("Topic {} does not exist yet; starting with the domains Redis already holds ({})",
                        Topics.SITE_EVENTS, sites.size());
                return;
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);

            int applied = 0;
            while (!caughtUp(consumer, end)) {
                ConsumerRecords<String, byte[]> records = consumer.poll(POLL);
                records.forEach(updates::apply);
                applied += records.count();
                if (records.isEmpty()) {
                    // The end offset of a compacted topic can sit past the last readable record.
                    break;
                }
            }
            log.info("Loaded the tracked domains from {}: {} records, {} domains",
                    Topics.SITE_EVENTS, applied, sites.size());
        }
    }

    private static List<TopicPartition> partitionsOf(Consumer<String, byte[]> consumer) {
        List<PartitionInfo> infos = consumer.partitionsFor(Topics.SITE_EVENTS);
        return infos == null ? List.of()
                : infos.stream().map(info -> new TopicPartition(info.topic(), info.partition())).toList();
    }

    private static boolean caughtUp(Consumer<String, byte[]> consumer, Map<TopicPartition, Long> end) {
        return end.entrySet().stream()
                .allMatch(partition -> consumer.position(partition.getKey()) >= partition.getValue());
    }
}
