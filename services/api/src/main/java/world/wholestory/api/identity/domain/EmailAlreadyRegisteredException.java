package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.AlreadyExistsException;

/**
 * Uniqueness spans every user, so no single aggregate can guarantee it: the unique index does, and this is what
 * the persistence adapter raises when the database refuses the insert.
 */
public class EmailAlreadyRegisteredException extends AlreadyExistsException {

    public EmailAlreadyRegisteredException(EmailAddress email) {
        super("already registered: " + email);
    }
}
