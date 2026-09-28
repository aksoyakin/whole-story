package world.wholestory.processor.enrichment;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.UuidV7;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnricherTest {

    private static final String CHROME = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    private static final String GOOGLEBOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final EventEnricher enricher = new EventEnricher(new UserAgentParser(), meters);

    @Test
    void dropsBotsAndKeepsVisitors() {
        List<EnrichedEvent> enriched = enricher.enrich(List.of(event(CHROME), event(GOOGLEBOT), event(CHROME)));

        assertThat(enriched).hasSize(2);
        assertThat(enriched).allSatisfy(e -> assertThat(e.client().browser()).isEqualTo("Chrome"));
    }

    @Test
    void countsDroppedBotsSoTheLossIsVisible() {
        enricher.enrich(List.of(event(GOOGLEBOT), event(CHROME), event("curl/8.7.1")));

        assertThat(meters.get("events.dropped").tag("reason", "bot").counter().count()).isEqualTo(2);
    }

    private static RawEventV1 event(String userAgent) {
        Instant now = Instant.parse("2026-09-28T10:00:00Z");
        return new RawEventV1(1, UuidV7.generate(now), now, UUID.randomUUID(), 1L, null, "pageview",
                "example.com", "/", null, null, null, null, null, null, null, null, null, userAgent, null);
    }
}
