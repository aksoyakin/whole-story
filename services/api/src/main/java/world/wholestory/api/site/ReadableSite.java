package world.wholestory.api.site;

import java.util.UUID;

/**
 * A site as the reporting side needs it.
 *
 * @param timezone IANA zone name; decides where this site's day starts when hourly UTC rollups are summed
 */
public record ReadableSite(UUID siteId, String domain, String timezone) {
}
