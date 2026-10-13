package world.wholestory.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * @param geoipDatabase                  MaxMind GeoLite2 City database; maintained by the geoipupdate sidecar
 * @param maxEventsPerVisitorPerMinute   admission limit per visitor hash, not per IP address (carrier NAT)
 * @param maxEventBytes                  largest request body one event may have. The limit above caps how many
 *                                       events a visitor may send, this one caps what each of them may cost
 */
@ConfigurationProperties("ingest")
public record IngestProperties(int topicPartitions,
                               Path geoipDatabase,
                               int maxEventsPerVisitorPerMinute,
                               int maxEventBytes) {
}
