package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.DomainException;

/** The message never contains the password itself. */
public class WeakPasswordException extends DomainException {

    public WeakPasswordException(int minLength, int maxLength) {
        super("a password must be between " + minLength + " and " + maxLength + " characters");
    }
}
