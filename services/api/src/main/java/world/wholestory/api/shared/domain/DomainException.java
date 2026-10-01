package world.wholestory.api.shared.domain;

/**
 * A domain rule was violated. Infrastructure translates these into HTTP responses, which is why they share a
 * base type: one exception handler, and no context has to repeat the mapping.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
