package world.wholestory.api.site;

import java.util.Optional;
import java.util.UUID;

/**
 * What other contexts may ask Site Management. Reporting needs one thing: may this person read this site, and
 * what does it need to know about it to answer in the site's own terms.
 * <p>
 * Authorization and the site's settings come back together on purpose. Reporting needs the timezone for every
 * query — a day starts where the site says it does (D-020) — and asking twice would mean two round trips and a
 * window where the answers disagree.
 * <p>
 * Plain identifiers: Identity and Site Management share typed ids because both have a domain model, while
 * Analytics deliberately has none, being the read side.
 */
public interface SiteAccess {

    /** Empty when the site does not exist, was removed, or belongs to an organization the user is not in. */
    Optional<ReadableSite> readableBy(UUID siteId, UUID userId);
}
