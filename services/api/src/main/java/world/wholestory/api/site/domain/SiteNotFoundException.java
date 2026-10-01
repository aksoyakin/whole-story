package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.NotFoundException;

/** Also raised for a site that exists but belongs to someone else, so the two cannot be told apart. */
public class SiteNotFoundException extends NotFoundException {

    public SiteNotFoundException() {
        super("no such site");
    }
}
