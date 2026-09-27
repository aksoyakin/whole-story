package world.wholestory.contracts;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Time-ordered UUIDs (RFC 9562, version 7). */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(Instant.now());
    }

    public static UUID generate(Instant time) {
        long unixMillis = time.toEpochMilli();
        long mostSignificant = (unixMillis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
