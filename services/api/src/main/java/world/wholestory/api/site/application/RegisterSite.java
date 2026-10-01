package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.OrganizationAccess;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.shared.domain.UserId;
import world.wholestory.api.site.domain.Domain;
import world.wholestory.api.site.domain.NotAnOrganizationMemberException;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;
import world.wholestory.api.site.domain.SiteLimitReachedException;
import world.wholestory.api.site.domain.Timezone;
import world.wholestory.contracts.UuidV7;

import java.time.Clock;
import java.time.Instant;

/**
 * Starts tracking a domain.
 * <p>
 * The recorded event is published inside the same transaction as the insert, which is what makes the outbox
 * work: the publication is stored next to the site, and only reaches Kafka once that transaction committed
 * (ADR 0009). Writing to Kafka directly here would be the dual write the architecture set out to avoid.
 */
@Service
@RequiredArgsConstructor
public class RegisterSite {

    private final SiteRepository sites;
    private final OrganizationAccess organizations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public SiteSummary register(RegisterSiteCommand command) {
        OrganizationId organizationId = OrganizationId.of(command.organizationId());
        UserId actor = UserId.of(command.actingUserId());
        if (!organizations.isMember(organizationId, actor)) {
            throw new NotAnOrganizationMemberException();
        }

        Domain domain = Domain.of(command.domain());
        Timezone timezone = command.timezone() == null || command.timezone().isBlank()
                ? Timezone.UTC
                : Timezone.of(command.timezone());

        // The plan's limit is the organization's rule, so the count goes to it rather than the limit coming here.
        if (!organizations.allowsAnotherSite(organizationId, sites.countByOrganization(organizationId))) {
            throw new SiteLimitReachedException();
        }

        Instant now = clock.instant();
        Site site = Site.register(SiteId.of(UuidV7.generate(now)), organizationId, domain, timezone, now);
        sites.save(site);
        site.pullRecordedEvents().forEach(events::publishEvent);
        return SiteMapper.toSummary(site);
    }
}
