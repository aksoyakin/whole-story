package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.DomainException;

public class InvalidEmailAddressException extends DomainException {

    public InvalidEmailAddressException(String value) {
        super("not an e-mail address: '" + value + "'");
    }
}
