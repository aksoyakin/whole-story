package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.DomainException;

/** The organization's plan has no room for another site. The limit itself belongs to the organization. */
public class SiteLimitReachedException extends DomainException {

    public SiteLimitReachedException() {
        super("this organization cannot add another site");
    }
}
