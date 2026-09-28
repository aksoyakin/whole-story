package world.wholestory.processor.enrichment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import world.wholestory.contracts.RawEventV1;

import java.util.ArrayList;
import java.util.List;

/**
 * Enrichment stage: derives the client from the User-Agent and removes events that are not visits.
 * Bots are dropped here, before sessionization, so they never create a session, a rollup or Redis state.
 */
@Component
public class EventEnricher {

    private final UserAgentParser userAgentParser;
    private final Counter botsDropped;

    EventEnricher(UserAgentParser userAgentParser, MeterRegistry meters) {
        this.userAgentParser = userAgentParser;
        this.botsDropped = Counter.builder("events.dropped")
                .tag("reason", "bot")
                .description("Events discarded because the User-Agent is not a human visitor")
                .register(meters);
    }

    public List<EnrichedEvent> enrich(List<RawEventV1> events) {
        List<EnrichedEvent> enriched = new ArrayList<>(events.size());
        for (RawEventV1 event : events) {
            userAgentParser.parse(event.userAgent())
                    .ifPresentOrElse(
                            client -> enriched.add(new EnrichedEvent(event, client)),
                            botsDropped::increment);
        }
        return enriched;
    }
}
