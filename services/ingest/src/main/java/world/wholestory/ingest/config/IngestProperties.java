package world.wholestory.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ingest")
public record IngestProperties(int topicPartitions) {
}
