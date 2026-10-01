package world.wholestory.api.shared.domain;

/**
 * The caller is known but may not do this. Used where refusing is not a secret: the caller named something they
 * already know about, such as an organization they claim to belong to.
 */
public abstract class NotPermittedException extends DomainException {

    protected NotPermittedException(String message) {
        super(message);
    }
}
