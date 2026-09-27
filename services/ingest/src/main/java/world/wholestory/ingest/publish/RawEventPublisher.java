package world.wholestory.ingest.publish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.Topics;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawEventPublisher {

    private final KafkaTemplate<String, byte[]> kafka;
    private final JsonMapper jsonMapper;

    public void publish(RawEventV1 event) {
        kafka.send(Topics.RAW_EVENTS, event.partitionKey(), jsonMapper.writeValueAsBytes(event))
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        log.error("Failed to publish event {}", event.eventId(), failure);
                    }
                });
    }
}
