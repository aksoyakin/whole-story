package world.wholestory.ingest.collect;

import java.util.Locale;

final class Domains {

    private Domains() {
    }

    static String normalize(String domain) {
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }
}
