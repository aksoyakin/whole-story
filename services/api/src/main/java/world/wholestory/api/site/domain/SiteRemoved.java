package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.DomainEvent;

import java.time.Instant;

/** A site stopped being tracked. Ingest drops the domain from its allow-list; stored data is purged separately. */
public record SiteRemoved(SiteId siteId, Domain domain, Instant occurredAt) implements DomainEvent {
}
