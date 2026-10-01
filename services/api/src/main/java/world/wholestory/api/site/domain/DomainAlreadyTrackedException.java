package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.AlreadyExistsException;

/**
 * A domain is tracked by exactly one site at a time, across every organization. Like any uniqueness rule it is
 * guaranteed by the index rather than by an aggregate; the persistence adapter raises this when the database
 * refuses the insert.
 */
public class DomainAlreadyTrackedException extends AlreadyExistsException {

    public DomainAlreadyTrackedException(Domain domain) {
        super("already tracked by another site: " + domain);
    }
}
