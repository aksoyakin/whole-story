package world.wholestory.api.site;

import java.util.UUID;

/**
 * What other contexts may ask Site Management. Reporting needs exactly one thing: may this person read this
 * site's statistics — which is true when they belong to the organization that owns it.
 * <p>
 * Plain identifiers on purpose. Identity and Site Management share typed ids because both have a domain model;
 * Analytics deliberately has none (it is the read side, SQL-first), so a value object would be the only one in
 * that module.
 */
public interface SiteAccess {

    boolean canRead(UUID siteId, UUID userId);
}
