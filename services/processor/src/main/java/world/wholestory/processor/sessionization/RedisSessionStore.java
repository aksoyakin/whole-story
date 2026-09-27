package world.wholestory.processor.sessionization;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Session state lives in Redis so that restarts and partition rebalances do not split sessions (D-029). */
@Component
@RequiredArgsConstructor
class RedisSessionStore implements SessionStore {

    private final StringRedisTemplate redis;

    @Override
    public Map<String, SessionState> load(Collection<String> keys) {
        Map<String, SessionState> sessions = new HashMap<>();
        if (keys.isEmpty()) {
            return sessions;
        }
        List<String> keyList = List.copyOf(keys);
        Iterator<String> values = redis.opsForValue().multiGet(keyList).iterator();
        for (String key : keyList) {
            String value = values.next();
            if (value != null) {
                sessions.put(key, SessionState.decode(value));
            }
        }
        return sessions;
    }

    @Override
    public void save(Map<String, SessionState> sessions) {
        long ttlSeconds = Sessionizer.SESSION_TIMEOUT.toSeconds();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            sessions.forEach((key, state) -> set(connection, key, state, ttlSeconds));
            return null;
        });
    }

    private static void set(RedisConnection connection, String key, SessionState state, long ttlSeconds) {
        connection.stringCommands().setEx(
                key.getBytes(StandardCharsets.UTF_8), ttlSeconds, state.encode().getBytes(StandardCharsets.UTF_8));
    }
}
