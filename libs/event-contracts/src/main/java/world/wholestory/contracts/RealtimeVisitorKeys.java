package world.wholestory.contracts;

import java.util.UUID;

/**
 * Where the "visitors right now" count lives in Redis.
 * <p>
 * Here for the same reason {@link Topics} is: a name two services have to spell identically. The processor
 * writes it — after the bot filter, so the number is visitors rather than crawlers — and api reads it. A
 * string duplicated on both sides would be a silent failure the first time one of them changed.
 * <p>
 * The value is a sorted set: member is the visitor hash, score is the second the visitor was last seen.
 * Counting is a range over the score rather than a count of keys, because counting keys means {@code SCAN}
 * over the whole keyspace. It also means the reader decides what "now" covers: trimming can lag without
 * making the answer wrong.
 */
public final class RealtimeVisitorKeys {

    private static final String PREFIX = "realtime:";

    private RealtimeVisitorKeys() {
    }

    public static String forSite(UUID siteId) {
        return PREFIX + siteId;
    }
}
