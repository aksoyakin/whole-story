package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.DomainEvent;

import java.time.Instant;

/**
 * Everything collected for a removed site may now be deleted.
 * <p>
 * Recorded alongside {@link SiteRemoved} rather than being the same event, because the two say different things
 * to different services and need different guarantees on the wire. {@code SiteRemoved} frees the domain for
 * ingest and is carried on a topic compacted by domain name; this one is a piece of work for the processor,
 * which owns the analytics schema and is the only service that may delete from it, and is carried keyed by site
 * id so that nothing else can ever compact it away. One domain event cannot be externalized to two topics, so
 * there are two.
 */
public record SiteDataPurgeRequested(SiteId siteId, Instant occurredAt) implements DomainEvent {
}
