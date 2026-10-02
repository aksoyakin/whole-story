package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.OrganizationAccess;
import world.wholestory.api.shared.domain.UserId;
import world.wholestory.api.site.ReadableGoal;
import world.wholestory.api.site.ReadableSite;
import world.wholestory.api.site.SiteAccess;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Answers what Analytics asks of this context. */
@Service
@RequiredArgsConstructor
class SiteAccessService implements SiteAccess {

    private final SiteRepository sites;
    private final GoalRepository goals;
    private final OrganizationAccess organizations;

    @Override
    @Transactional(readOnly = true)
    public Optional<ReadableSite> readableBy(UUID siteId, UUID userId) {
        return sites.findById(SiteId.of(siteId))
                .filter(site -> !site.isRemoved())
                .filter(site -> organizations.isMember(site.getOrganizationId(), UserId.of(userId)))
                .map(SiteAccessService::toReadable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReadableGoal> goalsOf(UUID siteId) {
        return goals.findBySite(SiteId.of(siteId)).stream().map(GoalMapper::toReadable).toList();
    }

    private static ReadableSite toReadable(Site site) {
        return new ReadableSite(site.getId().value(), site.getDomain().value(), site.getTimezone().value());
    }
}
