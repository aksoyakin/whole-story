package world.wholestory.api.shared.domain;

/**
 * Something that has to be unique already exists.
 * <p>
 * Uniqueness spans every aggregate of its kind, so no single aggregate can enforce it; the database does, and the
 * adapter raises this. It lives in the shared kernel so that the web layer can answer {@code 409} without reaching
 * into a context's internals — and because every context has a case like it: an e-mail address here, a tracked
 * domain in Site Management.
 */
public abstract class AlreadyExistsException extends DomainException {

    protected AlreadyExistsException(String message) {
        super(message);
    }
}
