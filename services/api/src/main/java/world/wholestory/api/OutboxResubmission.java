package world.wholestory.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Pushes publications that never reached their destination.
 * <p>
 * The registry resends on startup (D-110), which until now was the only retry there was: a broker or mail server
 * that was briefly unreachable left a publication sitting in {@code platform.event_publication} until the next
 * deploy. In production that is exactly what happened to a {@code SiteRegistered} announcement, and the symptom
 * showed up at the far end as a dashboard with no data in it.
 * <p>
 * Service plumbing rather than any context's concern, which is why it sits in the root package next to the
 * other service-wide configuration — the same reasoning that put the outbox table in its own schema (D-094).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxResubmission {

    /**
     * Old enough that a publication in flight is not resent underneath itself, short enough that nobody is left
     * waiting for a reset mail. Consumers are idempotent either way (ADR 0006, ADR 0019).
     */
    private static final Duration PENDING_FOR = Duration.ofMinutes(5);

    private final IncompleteEventPublications publications;

    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    void resubmitWhatNeverWentOut() {
        try {
            publications.resubmitIncompletePublicationsOlderThan(PENDING_FOR);
        } catch (RuntimeException e) {
            // A failing resubmission must not kill the schedule; the next run tries again.
            log.warn("Could not resubmit incomplete event publications", e);
        }
    }
}
