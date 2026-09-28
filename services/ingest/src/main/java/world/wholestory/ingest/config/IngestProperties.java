package world.wholestory.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * @param geoipDatabase MaxMind GeoLite2 City database; maintained by the geoipupdate sidecar in production
 */
@ConfigurationProperties("ingest")
public record IngestProperties(int topicPartitions, Path geoipDatabase) {
}
