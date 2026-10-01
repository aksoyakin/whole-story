package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.OrganizationAccess;
import world.wholestory.api.shared.domain.UserId;
import world.wholestory.api.site.SiteAccess;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;

import java.util.UUID;

/** Answers the one question Analytics asks of this context. */
@Service
@RequiredArgsConstructor
class SiteAccessService implements SiteAccess {

    private final SiteRepository sites;
    private final OrganizationAccess organizations;

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID siteId, UUID userId) {
        return sites.findById(SiteId.of(siteId))
                .filter(site -> !site.isRemoved())
                .map(Site::getOrganizationId)
                .filter(organization -> organizations.isMember(organization, UserId.of(userId)))
                .isPresent();
    }
}
