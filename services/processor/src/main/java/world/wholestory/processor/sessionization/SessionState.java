package world.wholestory.processor.sessionization;

import java.time.Instant;
import java.util.UUID;

record SessionState(UUID sessionId, Instant startedAt, Instant lastSeenAt) {

    SessionState seenAt(Instant time) {
        return time.isAfter(lastSeenAt) ? new SessionState(sessionId, startedAt, time) : this;
    }

    /**
     * Instants are stored losslessly: {@code startedAt} is part of the sessions primary key, so any precision loss
     * (e.g. truncating to milliseconds) would make the next batch insert a duplicate session row.
     */
    String encode() {
        return sessionId + "|" + startedAt + "|" + lastSeenAt;
    }

    static SessionState decode(String value) {
        String[] parts = value.split("\\|");
        return new SessionState(
                UUID.fromString(parts[0]),
                Instant.parse(parts[1]),
                Instant.parse(parts[2]));
    }
}
