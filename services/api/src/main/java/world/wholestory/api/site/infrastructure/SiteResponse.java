package world.wholestory.api.site.infrastructure;

import java.time.Instant;
import java.util.UUID;

record SiteResponse(UUID siteId, String domain, String timezone, boolean publicDashboard, Instant createdAt) {
}
