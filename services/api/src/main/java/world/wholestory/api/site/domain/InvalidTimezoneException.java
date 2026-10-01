package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.DomainException;

public class InvalidTimezoneException extends DomainException {

    public InvalidTimezoneException(String value) {
        super("not a known timezone: '" + value + "'");
    }
}
