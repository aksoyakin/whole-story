package world.wholestory.processor.sessionization;

import world.wholestory.contracts.RawEventV1;

import java.time.Instant;
import java.util.UUID;

public record SessionizedEvent(RawEventV1 event, UUID sessionId, Instant sessionStartedAt) {
}
