package world.wholestory.api.site.application;

import java.time.Instant;
import java.util.UUID;

/** What a dashboard needs to list a site. */
public record SiteSummary(UUID siteId, String domain, String timezone, boolean publicDashboard, Instant createdAt) {
}
