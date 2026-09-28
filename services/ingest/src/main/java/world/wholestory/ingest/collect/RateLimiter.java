package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import world.wholestory.ingest.config.IngestProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Caps how many events one visitor can report per minute, which is what stops someone inflating a site's numbers.
 * <p>
 * The bucket is the visitor hash rather than the IP address. Carrier-grade NAT puts thousands of real people behind
 * one address, so an IP bucket would drop genuine visits on a popular site, and for an analytics product a dropped
 * visit is a wrong number. Protecting the infrastructure itself from a flood is left to the reverse proxy.
 */
@Component
@RequiredArgsConstructor
class RateLimiter {

    /** Two windows, so a bucket always outlives the minute it counts. */
    private static final Duration BUCKET_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final IngestProperties properties;

    boolean allows(UUID siteId, long visitorHash) {
        String key = "rate:" + clock.instant().getEpochSecond() / 60 + ":" + siteId + ":" + visitorHash;
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return true; // Redis cannot answer: accepting the event beats losing it
        }
        if (count == 1L) {
            redis.expire(key, BUCKET_TTL);
        }
        return count <= properties.maxEventsPerVisitorPerMinute();
    }
}
