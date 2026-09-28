package world.wholestory.processor.sessionization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.UuidV7;
import world.wholestory.processor.enrichment.EnrichedEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Groups events into sessions: a new session starts after 30 minutes of inactivity.
 * Events of one visitor arrive in order because the Kafka key is {@code siteId:visitorHash} (D-019).
 */
@Component
@RequiredArgsConstructor
public class Sessionizer {

    static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final SessionStore store;

    public List<SessionizedEvent> sessionize(List<EnrichedEvent> events) {
        Map<String, SessionState> sessions = new HashMap<>(store.load(keysOf(events)));
        Map<String, SessionState> changed = new HashMap<>();
        List<SessionizedEvent> result = new ArrayList<>(events.size());

        for (EnrichedEvent enriched : events) {
            RawEventV1 event = enriched.event();
            String key = key(event.siteId(), event.visitorHash());
            SessionState session = sessions.get(key);
            if (session == null && event.previousVisitorHash() != null) {
                // Salt rotated at midnight: continue the visitor's session under the new hash (D-028).
                session = sessions.get(key(event.siteId(), event.previousVisitorHash()));
            }
            if (session == null || isExpired(session, event)) {
                session = new SessionState(UuidV7.generate(event.timestamp()), event.timestamp(), event.timestamp());
            } else {
                session = session.seenAt(event.timestamp());
            }
            sessions.put(key, session);
            changed.put(key, session);
            result.add(new SessionizedEvent(enriched, session.sessionId(), session.startedAt()));
        }

        store.save(changed);
        return result;
    }

    private static boolean isExpired(SessionState session, RawEventV1 event) {
        return Duration.between(session.lastSeenAt(), event.timestamp()).compareTo(SESSION_TIMEOUT) > 0;
    }

    private static Set<String> keysOf(List<EnrichedEvent> events) {
        Set<String> keys = new LinkedHashSet<>();
        for (EnrichedEvent enriched : events) {
            RawEventV1 event = enriched.event();
            keys.add(key(event.siteId(), event.visitorHash()));
            if (event.previousVisitorHash() != null) {
                keys.add(key(event.siteId(), event.previousVisitorHash()));
            }
        }
        return keys;
    }

    static String key(UUID siteId, long visitorHash) {
        return "session:" + siteId + ":" + visitorHash;
    }
}
