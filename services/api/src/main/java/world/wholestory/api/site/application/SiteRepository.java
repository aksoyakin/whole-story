package world.wholestory.api.site.application;

import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;

import java.util.List;
import java.util.Optional;

/**
 * Port to stored sites. The adapter turns a violated uniqueness index into {@code DomainAlreadyTrackedException}:
 * one domain belongs to one site across every organization, which no aggregate can see.
 */
public interface SiteRepository {

    void save(Site site);

    /** Finds a site whether or not it was removed, so that removing one twice stays harmless. */
    Optional<Site> findById(SiteId id);

    /** Live sites only: a removed site is gone as far as the dashboard is concerned. */
    List<Site> findByOrganization(OrganizationId organizationId);

    /** Live sites only — a removed site must not keep a slot in the organization's plan. */
    int countByOrganization(OrganizationId organizationId);
}
