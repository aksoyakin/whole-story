package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import world.wholestory.api.identity.OrganizationAccess;
import world.wholestory.api.shared.domain.UserId;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;
import world.wholestory.api.site.domain.SiteNotFoundException;

import java.util.UUID;

/**
 * "Is this site this person's to work with?" — asked by every use case that acts on one site, so it is
 * answered in one place.
 * A site that was removed, and a site belonging to an organization the caller is not in, both answer exactly as
 * one that never existed: an id must not reveal what exists.
 */
@Service
@RequiredArgsConstructor
class ReadableSites {

    private final SiteRepository sites;
    private final OrganizationAccess organizations;

    Site require(UUID siteId, UUID actingUserId) {
        Site site = sites.findById(SiteId.of(siteId)).orElseThrow(SiteNotFoundException::new);
        if (site.isRemoved() || !organizations.isMember(site.getOrganizationId(), UserId.of(actingUserId))) {
            throw new SiteNotFoundException();
        }
        return site;
    }
}
