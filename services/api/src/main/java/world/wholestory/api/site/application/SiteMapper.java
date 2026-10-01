package world.wholestory.api.site.application;

import world.wholestory.api.site.domain.Site;

/** Hand-written, like every mapper in this project. */
final class SiteMapper {

    private SiteMapper() {
    }

    static SiteSummary toSummary(Site site) {
        return new SiteSummary(
                site.getId().value(),
                site.getDomain().value(),
                site.getTimezone().value(),
                site.isPublicDashboard(),
                site.getCreatedAt());
    }
}
