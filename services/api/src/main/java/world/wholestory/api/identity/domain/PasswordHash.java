package world.wholestory.api.identity.domain;

/**
 * The stored form of a password. Which algorithm produced it is an infrastructure decision; the domain only
 * carries the value, which is why the algorithm prefix travels inside it (ADR 0018).
 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a password hash is required");
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }

    @Override
    public String toString() {
        return "PasswordHash(hidden)";
    }
}
