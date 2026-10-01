package world.wholestory.processor.sessionization;

import org.junit.jupiter.api.Test;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.UuidV7;
import world.wholestory.processor.enrichment.ClientProfile;
import world.wholestory.processor.enrichment.EnrichedEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionizerTest {

    private static final UUID SITE = UUID.fromString("0199a1b2-0000-7000-8000-000000000001");
    private static final Instant T0 = Instant.parse("2026-09-26T10:00:00Z");

    private final InMemorySessionStore store = new InMemorySessionStore();
    private final Sessionizer sessionizer = new Sessionizer(store);

    @Test
    void keepsEventsWithinThirtyMinutesInOneSession() {
        List<SessionizedEvent> result = sessionizer.sessionize(List.of(
                pageview(1L, T0), pageview(1L, T0.plusSeconds(29 * 60))));

        assertThat(result.get(1).sessionId()).isEqualTo(result.get(0).sessionId());
        assertThat(result.get(1).sessionStartedAt()).isEqualTo(T0);
    }

    @Test
    void startsANewSessionAfterThirtyMinutesOfInactivity() {
        List<SessionizedEvent> result = sessionizer.sessionize(List.of(
                pageview(1L, T0), pageview(1L, T0.plusSeconds(31 * 60))));

        assertThat(result.get(1).sessionId()).isNotEqualTo(result.get(0).sessionId());
    }

    @Test
    void continuesSessionsAcrossBatchesThroughTheStore() {
        UUID first = sessionizer.sessionize(List.of(pageview(1L, T0))).getFirst().sessionId();
        UUID second = sessionizer.sessionize(List.of(pageview(1L, T0.plusSeconds(60)))).getFirst().sessionId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void separatesVisitors() {
        List<SessionizedEvent> result = sessionizer.sessionize(List.of(pageview(1L, T0), pageview(2L, T0)));

        assertThat(result.get(1).sessionId()).isNotEqualTo(result.get(0).sessionId());
    }

    @Test
    void continuesTheSessionWhenTheDailySaltRotates() {
        UUID beforeMidnight = sessionizer.sessionize(List.of(pageview(1L, T0))).getFirst().sessionId();

        EnrichedEvent afterRotation = event(99L, 1L, T0.plusSeconds(60));
        UUID afterMidnight = sessionizer.sessionize(List.of(afterRotation)).getFirst().sessionId();

        assertThat(afterMidnight).isEqualTo(beforeMidnight);
    }

    private static EnrichedEvent pageview(long visitorHash, Instant at) {
        return event(visitorHash, null, at);
    }

    private static EnrichedEvent event(long visitorHash, Long previousVisitorHash, Instant at) {
        RawEventV1 raw = new RawEventV1(1, UuidV7.generate(at), at, SITE, visitorHash, previousVisitorHash, "pageview",
                "example.com", "/", null, null, null, null, null, null, null, null, null, null, null, "UA", null);
        return new EnrichedEvent(raw, ClientProfile.UNKNOWN, null);
    }

    private static final class InMemorySessionStore implements SessionStore {

        private final Map<String, SessionState> sessions = new HashMap<>();

        @Override
        public Map<String, SessionState> load(Collection<String> keys) {
            Map<String, SessionState> found = new HashMap<>();
            keys.forEach(key -> {
                if (sessions.containsKey(key)) {
                    found.put(key, sessions.get(key));
                }
            });
            return found;
        }

        @Override
        public void save(Map<String, SessionState> changed) {
            sessions.putAll(changed);
        }
    }
}
