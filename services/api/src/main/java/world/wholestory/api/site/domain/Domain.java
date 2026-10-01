package world.wholestory.api.site.domain;

import java.util.Locale;

/**
 * The host a site is tracked under, normalised so that one site cannot be registered twice: lower-cased, trimmed
 * and without a leading {@code www.}. The tracker sends the same normalisation from the browser, and ingest
 * applies it again before the allow-list lookup.
 * <p>
 * Only a hostname is accepted — no scheme, no port, no path. Those would make two spellings of one site look
 * different, and the value is compared, not fetched.
 */
public record Domain(String value) {

    /** Longest hostname a DNS name can be. */
    private static final int MAX_LENGTH = 253;
    private static final int MAX_LABEL_LENGTH = 63;
    private static final String WWW = "www.";

    public Domain {
        value = normalise(value);
        if (!isHostname(value)) {
            throw new InvalidDomainException(value);
        }
    }

    public static Domain of(String value) {
        return new Domain(value);
    }

    private static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        return normalised.startsWith(WWW) ? normalised.substring(WWW.length()) : normalised;
    }

    private static boolean isHostname(String candidate) {
        if (candidate.isEmpty() || candidate.length() > MAX_LENGTH || !candidate.contains(".")) {
            return false;
        }
        for (String label : candidate.split("\\.", -1)) {
            if (!isLabel(label)) {
                return false;
            }
        }
        // A bare address is not a site: the dashboard is organised by name, and an address is not stable.
        return !candidate.chars().allMatch(c -> c == '.' || (c >= '0' && c <= '9'));
    }

    private static boolean isLabel(String label) {
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH
                || label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        return label.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-');
    }

    @Override
    public String toString() {
        return value;
    }
}
