package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.DomainException;

public class InvalidDomainException extends DomainException {

    public InvalidDomainException(String value) {
        super("not a hostname: '" + value + "'");
    }
}
