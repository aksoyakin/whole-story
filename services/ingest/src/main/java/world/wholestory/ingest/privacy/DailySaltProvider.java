package world.wholestory.ingest.privacy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One random salt per UTC day, shared by all ingest instances through Redis.
 * Salts expire on their own, so yesterday's visitors can never be linked to today's.
 */
@Component
@RequiredArgsConstructor
public class DailySaltProvider {

    /** A day plus the 30 minute rotation grace period (D-028), rounded up. */
    private static final Duration SALT_TTL = Duration.ofHours(25);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AtomicReference<DailySalt> cached = new AtomicReference<>();

    public byte[] saltFor(LocalDate utcDate) {
        DailySalt current = cached.get();
        if (current != null && current.date().equals(utcDate)) {
            return current.value();
        }
        String key = "salt:" + utcDate;
        redis.opsForValue().setIfAbsent(key, newSalt(), SALT_TTL);
        byte[] value = Base64.getDecoder().decode(redis.opsForValue().get(key));
        cached.set(new DailySalt(utcDate, value));
        return value;
    }

    private static String newSalt() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private record DailySalt(LocalDate date, byte[] value) {
    }
}
