package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.DomainException;

/**
 * One error for every way a reset link can fail to work: unknown, already used, expired, or not a token at all.
 * Telling them apart would tell whoever is holding the link which of those it is, and none of that is their
 * business — the answer is the same either way, ask for a new link.
 */
public class InvalidResetTokenException extends DomainException {

    public InvalidResetTokenException() {
        super("this reset link cannot be used");
    }
}
