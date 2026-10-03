package world.wholestory.api;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * @see OutboxResubmission for what the schedule is for
 */
@Configuration
@EnableScheduling
// Spring Modulith's @ApplicationModuleListener is an @Async listener, and Boot does not switch @Async on by
// itself: without this the password reset mail would be sent on the request thread.
@EnableAsync
class ApiConfig {

    /** Injected rather than read statically, so that time can be fixed in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * How many publications never reached their destination.
     * <p>
     * Two decisions put a failure here instead of in the health endpoint. The mail health indicator was turned
     * off because it dialled SMTP on every check (D-127), and what it was replaced by was this sentence: a send
     * that fails leaves its publication row incomplete, which is the honest record of it. Nothing read that
     * record. The same holds for a broker that is briefly away (D-110, D-115).
     * <p>
     * A number that sits above zero for longer than {@code OutboxResubmission}'s five minutes means the
     * resubmission is not getting through either — a reset mail nobody received, or a site the ingest service
     * never heard about. That is the shape the production incident behind D-115 had.
     * <p>
     * Read on scrape rather than kept in a field: the index on {@code completion_date} makes it a cheap lookup,
     * and a stale copy of this number would be worse than no number. If the query fails the gauge reports
     * nothing at all ({@code NaN}) — a zero here would claim the queue is empty, which is the one lie that
     * matters.
     */
    @Bean
    MeterBinder outboxMetrics(JdbcClient jdbc) {
        return registry -> Gauge.builder("outbox.publications.incomplete", () -> incompletePublications(jdbc))
                .description("Event publications written but not yet confirmed by their listener")
                .register(registry);
    }

    private static double incompletePublications(JdbcClient jdbc) {
        try {
            return jdbc.sql("select count(*) from platform.event_publication where completion_date is null")
                    .query(Long.class)
                    .single();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
