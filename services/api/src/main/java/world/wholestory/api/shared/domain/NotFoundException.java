package world.wholestory.api.shared.domain;

/**
 * The thing asked for is not there — or is there and does not belong to the caller. Both answer the same, so
 * that an id cannot be used to find out what exists.
 */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String message) {
        super(message);
    }
}
