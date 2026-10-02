package world.wholestory.api.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The secret in a password reset link. It is the one thing that proves whoever follows the link can read the
 * account's mail, so it is treated like a password: never stored, never logged.
 * <p>
 * Only its {@link #hash()} is persisted. A reset link is a credential with a one hour life, and a database dump
 * must not be enough to mint one — the same reason a password is stored hashed.
 */
public record ResetToken(String value) {

    /** 256 bits of randomness, which is past the point where guessing is the attack anyone would choose. */
    private static final int BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ResetToken {
        if (value == null || value.isBlank()) {
            throw new InvalidResetTokenException();
        }
    }

    /** Base64url, so the value survives a query string without escaping. */
    public static ResetToken generate() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return new ResetToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** Wraps what arrived from a link, which may be anything at all. */
    public static ResetToken of(String value) {
        return new ResetToken(value);
    }

    /**
     * SHA-256, unsalted and uniterated on purpose: unlike a password this is 256 random bits with a one hour
     * life, so there is no dictionary to stretch against and nothing a work factor would buy.
     */
    public ResetTokenHash hash() {
        return ResetTokenHash.of(HexFormat.of().formatHex(sha256().digest(value.getBytes(StandardCharsets.UTF_8))));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK", e);
        }
    }

    @Override
    public String toString() {
        return "ResetToken(hidden)";
    }
}
