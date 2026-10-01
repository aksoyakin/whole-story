package world.wholestory.api.analytics.application;

import world.wholestory.api.shared.domain.NotFoundException;

/**
 * The site does not exist, or exists and belongs to an organization the caller is not a member of. One answer
 * for both, so that a site id cannot be used to find out whether it is in use.
 */
public class SiteNotVisibleException extends NotFoundException {

    public SiteNotVisibleException() {
        super("no such site");
    }
}
