package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.OrganizationAccess;
import world.wholestory.api.shared.domain.UserId;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;
import world.wholestory.api.site.domain.SiteNotFoundException;

import java.time.Clock;
import java.util.UUID;

/**
 * Stops tracking a site. The domain frees up at once and ingest hears about it through the same outbox;
 * the data that was already collected is removed afterwards by a purge job (D-034).
 */
@Service
@RequiredArgsConstructor
public class RemoveSite {

    private final SiteRepository sites;
    private final OrganizationAccess organizations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void remove(UUID siteId, UUID actingUserId) {
        Site site = sites.findById(SiteId.of(siteId)).orElseThrow(SiteNotFoundException::new);
        // Someone else's site answers the same as one that does not exist.
        if (!organizations.isMember(site.getOrganizationId(), UserId.of(actingUserId))) {
            throw new SiteNotFoundException();
        }
        site.remove(clock.instant());
        sites.save(site);
        site.pullRecordedEvents().forEach(events::publishEvent);
    }
}
