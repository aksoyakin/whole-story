package world.wholestory.api.identity.domain;

/**
 * The stored form of a {@link ResetToken}: what a lookup compares against, and the only part of the secret that
 * reaches the database.
 */
public record ResetTokenHash(String value) {

    public ResetTokenHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a reset token hash is required");
        }
    }

    public static ResetTokenHash of(String value) {
        return new ResetTokenHash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
