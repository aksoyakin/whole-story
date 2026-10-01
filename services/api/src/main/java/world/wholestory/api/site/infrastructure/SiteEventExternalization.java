package world.wholestory.api.site.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import world.wholestory.api.site.domain.SiteRegistered;
import world.wholestory.api.site.domain.SiteRemoved;
import world.wholestory.contracts.Topics;
import world.wholestory.contracts.TrackedDomainV1;

/**
 * Carries this context's domain events out to Kafka through the event publication registry (ADR 0009): the
 * publication is written in the same transaction as the site, and only sent after that transaction committed.
 * <p>
 * Configured in code rather than with {@code @Externalized}, because the annotation would have to sit on the
 * domain event — and the domain layer stays free of frameworks (ADR 0010). The versioned record that goes on the
 * wire lives in {@code event-contracts}, so the domain event never becomes a published contract (D-041), and
 * both events map to the same record: on a compacted topic what matters is the state of a domain, not which
 * of the two things happened to it.
 */
@Configuration
class SiteEventExternalization {

    @Bean
    EventExternalizationConfiguration siteEventRouting() {
        return EventExternalizationConfiguration.externalizing()
                .select(event -> event instanceof SiteRegistered || event instanceof SiteRemoved)
                .mapping(SiteRegistered.class, event -> TrackedDomainV1.tracked(
                        event.domain().value(),
                        event.siteId().value(),
                        event.organizationId().value(),
                        event.occurredAt()))
                .mapping(SiteRemoved.class, event -> TrackedDomainV1.untracked(
                        event.domain().value(), event.siteId().value(), event.occurredAt()))
                .route(SiteRegistered.class, event -> target(event.domain().value()))
                .route(SiteRemoved.class, event -> target(event.domain().value()))
                .build();
    }

    /** Keyed on the domain, which is what compaction collapses: the last word about a domain is the current one. */
    private static RoutingTarget target(String domain) {
        return RoutingTarget.forTarget(Topics.SITE_EVENTS).andKey(domain);
    }

    /**
     * One partition: this is broadcast state, not work to divide, and every consumer reads all of it anyway.
     * Compaction is what lets a consumer with an empty cache rebuild from the log.
     */
    @Bean
    NewTopic siteEventsTopic() {
        return TopicBuilder.name(Topics.SITE_EVENTS)
                .partitions(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }
}
