package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Which domains are tracked, kept in Redis for a lookup on every event.
 * <p>
 * Redis is a cache here, not the source of truth: the truth is the compacted {@code site-events} topic, which
 * Site Management writes through its outbox. That is what lets an instance with an empty Redis rebuild the whole
 * list instead of silently rejecting every event.
 */
@Component
@RequiredArgsConstructor
class SiteRegistry {

    static final String KEY = "sites:domains";

    private final StringRedisTemplate redis;

    Optional<UUID> findSiteId(String normalizedDomain) {
        Object siteId = redis.opsForHash().get(KEY, normalizedDomain);
        return Optional.ofNullable(siteId).map(Object::toString).map(UUID::fromString);
    }

    void track(String domain, UUID siteId) {
        redis.opsForHash().put(KEY, Domains.normalize(domain), siteId.toString());
    }

    void forget(String domain) {
        redis.opsForHash().delete(KEY, Domains.normalize(domain));
    }

    long size() {
        Long size = redis.opsForHash().size(KEY);
        return size == null ? 0L : size;
    }
}
