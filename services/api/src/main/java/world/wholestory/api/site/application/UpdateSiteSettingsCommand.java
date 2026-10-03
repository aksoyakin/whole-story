package world.wholestory.api.site.application;

import java.util.UUID;

/**
 * The settings a site owner may change, all of them, every time.
 * <p>
 * Deliberately not a partial update: a field left out would have to mean "leave this alone", which makes the
 * absent value and the chosen value indistinguishable in the same way two nullable fields did for a goal's
 * target (D-123). The form holds both values anyway, so sending both costs nothing.
 *
 * @param timezone        IANA zone name; decides where this site's day starts (D-020)
 * @param publicDashboard whether the read-only dashboard is open to anyone with the link
 */
public record UpdateSiteSettingsCommand(UUID siteId, UUID actingUserId, String timezone, boolean publicDashboard) {
}
