package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.DomainException;

public class InvalidNameException extends DomainException {

    public InvalidNameException(String message) {
        super(message);
    }
}
