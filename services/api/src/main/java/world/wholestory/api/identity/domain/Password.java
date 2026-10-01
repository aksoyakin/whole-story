package world.wholestory.api.identity.domain;

/**
 * A password on its way to being hashed. It is never stored and never logged: {@code toString} hides the value,
 * because a value object ends up inside exception messages and log lines sooner or later (ADR 0018).
 * <p>
 * Only the length is constrained. Composition rules — an upper-case letter, a digit, a symbol — mostly produce
 * predictable substitutions rather than stronger passwords, which is why NIST SP 800-63B advises against them.
 */
public record Password(String value) {

    public static final int MIN_LENGTH = 8;
    /** Long enough for any passphrase, short enough that hashing cannot be used to burn CPU. */
    public static final int MAX_LENGTH = 200;

    public Password {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new WeakPasswordException(MIN_LENGTH, MAX_LENGTH);
        }
    }

    public static Password of(String value) {
        return new Password(value);
    }

    @Override
    public String toString() {
        return "Password(hidden)";
    }
}
