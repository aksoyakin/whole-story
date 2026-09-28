package world.wholestory.processor.sessionization;

import world.wholestory.contracts.RawEventV1;
import world.wholestory.processor.enrichment.ClientProfile;
import world.wholestory.processor.enrichment.EnrichedEvent;

import java.time.Instant;
import java.util.UUID;

public record SessionizedEvent(EnrichedEvent enriched, UUID sessionId, Instant sessionStartedAt) {

    public RawEventV1 event() {
        return enriched.event();
    }

    public ClientProfile client() {
        return enriched.client();
    }

    public String referrerSource() {
        return enriched.referrerSource();
    }
}
