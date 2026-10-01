package world.wholestory.api.site.infrastructure;

import world.wholestory.api.site.application.SiteSummary;

final class SiteResponseMapper {

    private SiteResponseMapper() {
    }

    static SiteResponse toResponse(SiteSummary summary) {
        return new SiteResponse(
                summary.siteId(),
                summary.domain(),
                summary.timezone(),
                summary.publicDashboard(),
                summary.createdAt());
    }
}
