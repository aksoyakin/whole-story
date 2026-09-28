package world.wholestory.ingest.privacy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One random salt per UTC day, shared by all ingest instances through Redis.
 * <p>
 * A salt expires 30 minutes into the following day, which is exactly the rotation grace period (D-028): long
 * enough to keep a session that crosses midnight together, and no longer. The deadline is absolute rather than a
 * duration from first use, because salts are created lazily: a relative time-to-live would keep yesterday's salt
 * alive for as long as a day after the grace period ended, and with it the ability to link visitors across days.
 */
@Component
@RequiredArgsConstructor
public class DailySaltProvider implements DailySalts {

    static final Duration ROTATION_GRACE = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "salt:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<LocalDate, byte[]> cache = new ConcurrentHashMap<>();

    @Override
    public byte[] saltFor(LocalDate utcDate) {
        byte[] cached = cache.get(utcDate);
        if (cached != null) {
            return cached;
        }
        String key = KEY_PREFIX + utcDate;
        Duration ttl = Duration.between(clock.instant(), expiryOf(utcDate));
        if (!ttl.isPositive()) {
            throw new IllegalArgumentException("salt for " + utcDate + " has already expired");
        }
        redis.opsForValue().setIfAbsent(key, newSalt(), ttl);
        return remember(utcDate, decode(redis.opsForValue().get(key)));
    }

    @Override
    public Optional<byte[]> existingSaltFor(LocalDate utcDate) {
        byte[] cached = cache.get(utcDate);
        if (cached != null) {
            return Optional.of(cached);
        }
        return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + utcDate))
                .map(value -> remember(utcDate, decode(value)));
    }

    /** A salt lives until the rotation grace period of the next day has passed. */
    static Instant expiryOf(LocalDate utcDate) {
        return utcDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().plus(ROTATION_GRACE);
    }

    private byte[] remember(LocalDate utcDate, byte[] salt) {
        cache.put(utcDate, salt);
        // Only today and yesterday can be needed; anything older would keep an expired secret in memory.
        cache.keySet().removeIf(date -> date.isBefore(utcDate.minusDays(1)));
        return salt;
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static String newSalt() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
