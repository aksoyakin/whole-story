package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.DomainException;

/** The goal as described could never match anything. The message says what to write instead. */
public class InvalidGoalException extends DomainException {

    public InvalidGoalException(String message) {
        super(message);
    }
}
