package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Allow-list of tracked domains, kept in Redis by Site Management (SiteRegistered / SiteDeleted events).
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
}
