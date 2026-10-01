package world.wholestory.api.identity;

import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.shared.domain.UserId;

/**
 * What other contexts may ask Identity &amp; Access. This is the module's whole published surface: everything
 * else lives in its internal packages.
 * <p>
 * Questions come through here; state changes travel as events. Both answers belong to this context because both
 * are properties of the {@code Organization} aggregate — a caller that fetched the limit and compared it itself
 * would have taken the rule with it.
 */
public interface OrganizationAccess {

    boolean isMember(OrganizationId organizationId, UserId userId);

    /**
     * @param currentSiteCount how many sites the organization already has; counted by the caller, because Site
     *                         Management owns sites and this context must not read them
     */
    boolean allowsAnotherSite(OrganizationId organizationId, int currentSiteCount);
}
