package world.wholestory.api.identity.domain;

import java.util.Locale;

/**
 * A normalised e-mail address: trimmed and lower-cased, so that the same person cannot hold two accounts that
 * differ only in capitalisation. The column is {@code citext} as well, which makes the uniqueness index agree.
 * <p>
 * Only the structure is checked. Whether an address exists is answered by sending mail to it, not by a pattern,
 * and a stricter expression would reject valid addresses long before it caught an invented one.
 */
public record EmailAddress(String value) {

    /** The longest path an SMTP server has to accept (RFC 5321). */
    private static final int MAX_LENGTH = 254;

    public EmailAddress {
        value = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!isStructurallyValid(value)) {
            throw new InvalidEmailAddressException(value);
        }
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }

    private static boolean isStructurallyValid(String candidate) {
        if (candidate.isEmpty() || candidate.length() > MAX_LENGTH) {
            return false;
        }
        int at = candidate.indexOf('@');
        if (at <= 0 || at != candidate.lastIndexOf('@') || at == candidate.length() - 1) {
            return false;
        }
        String domain = candidate.substring(at + 1);
        return !containsWhitespace(candidate)
                && domain.indexOf('.') > 0
                && !domain.startsWith(".")
                && !domain.endsWith(".");
    }

    private static boolean containsWhitespace(String candidate) {
        for (int i = 0; i < candidate.length(); i++) {
            if (Character.isWhitespace(candidate.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return value;
    }
}
