package world.wholestory.contracts;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A tracker event after ingest has applied privacy processing.
 * The client IP address never appears here: it is reduced to {@code visitorHash} and geo fields inside ingest.
 */
public record RawEventV1(
        int schemaVersion,
        UUID eventId,
        Instant timestamp,
        UUID siteId,
        long visitorHash,
        Long previousVisitorHash,
        String name,
        String hostname,
        String pathname,
        String referrer,
        String utmSource,
        String utmMedium,
        String utmCampaign,
        String utmContent,
        String utmTerm,
        String countryCode,
        String subdivisionCode,
        Integer cityGeonameId,
        String userAgent,
        Map<String, String> props
) {

    public static final int SCHEMA_VERSION = 1;
    public static final String PAGEVIEW = "pageview";

    /** Not a bean-style getter on purpose: derived values must not leak into the serialized contract. */
    public boolean representsPageview() {
        return PAGEVIEW.equals(name);
    }

    /** Kafka key: keeps all events of a visitor on one partition, in order. */
    public String partitionKey() {
        return siteId + ":" + visitorHash;
    }
}
