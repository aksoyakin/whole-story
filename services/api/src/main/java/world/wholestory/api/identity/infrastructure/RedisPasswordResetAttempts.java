package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import world.wholestory.api.identity.application.PasswordResetAttempts;
import world.wholestory.api.shared.domain.UserId;

import java.time.Duration;

/**
 * A counter per account, in the same shape as ingest's rate limiter: one fixed window, expired by Redis.
 * Three links an hour is more than anyone needs and far less than a mail host's allowance.
 */
@Component
@RequiredArgsConstructor
class RedisPasswordResetAttempts implements PasswordResetAttempts {

    private static final int MAX_PER_WINDOW = 3;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    @Override
    public boolean allowAnother(UserId userId) {
        String key = "password-reset:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return true; // Redis cannot answer: sending the mail beats locking someone out of their account
        }
        if (count == 1L) {
            redis.expire(key, WINDOW);
        }
        return count <= MAX_PER_WINDOW;
    }
}
