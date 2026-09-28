package world.wholestory.processor.enrichment;

import world.wholestory.contracts.RawEventV1;

/** A raw event plus everything the enrichment stage derived from it. */
public record EnrichedEvent(RawEventV1 event, ClientProfile client, String referrerSource) {
}
