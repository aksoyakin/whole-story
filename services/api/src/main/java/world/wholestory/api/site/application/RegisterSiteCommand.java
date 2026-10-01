package world.wholestory.api.site.application;

import java.util.UUID;

/**
 * @param timezone IANA zone name, or null for UTC. It decides where a day starts when hourly rollups are
 *                 summed, so it belongs to the site rather than to whoever is looking at the dashboard (D-020).
 */
public record RegisterSiteCommand(UUID organizationId, UUID actingUserId, String domain, String timezone) {
}
