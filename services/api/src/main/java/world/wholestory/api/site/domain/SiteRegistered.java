package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.DomainEvent;
import world.wholestory.api.shared.domain.OrganizationId;

import java.time.Instant;

/** A site started being tracked. Carried out of the application as a Kafka message so that ingest learns of it. */
public record SiteRegistered(SiteId siteId, OrganizationId organizationId, Domain domain, Instant occurredAt)
        implements DomainEvent {
}
