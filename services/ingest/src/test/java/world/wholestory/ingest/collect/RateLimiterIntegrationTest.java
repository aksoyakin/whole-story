package world.wholestory.ingest.collect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import world.wholestory.ingest.TestcontainersConfiguration;
import world.wholestory.ingest.config.IngestProperties;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RateLimiterIntegrationTest {

    private static final int LIMIT = 3;
    private static final Instant MINUTE = Instant.parse("2126-09-28T10:00:30Z");

    @Autowired
    StringRedisTemplate redis;

    private RateLimiter limiterAt(Instant now) {
        return new RateLimiter(redis, Clock.fixed(now, ZoneOffset.UTC),
                new IngestProperties(1, Path.of("unused"), LIMIT));
    }

    @Test
    void allowsUpToTheLimitAndRejectsAfterwards() {
        RateLimiter limiter = limiterAt(MINUTE);
        UUID site = UUID.randomUUID();

        assertThat(IntStream.range(0, LIMIT).allMatch(i -> limiter.allows(site, 1L))).isTrue();
        assertThat(limiter.allows(site, 1L)).isFalse();
    }

    @Test
    void countsEachVisitorSeparately() {
        RateLimiter limiter = limiterAt(MINUTE);
        UUID site = UUID.randomUUID();
        IntStream.range(0, LIMIT).forEach(i -> limiter.allows(site, 1L));

        // A different visitor on the same site starts from zero: NAT neighbours must not limit each other.
        assertThat(limiter.allows(site, 2L)).isTrue();
    }

    @Test
    void startsOverInTheNextMinute() {
        UUID site = UUID.randomUUID();
        RateLimiter limiter = limiterAt(MINUTE);
        IntStream.range(0, LIMIT).forEach(i -> limiter.allows(site, 3L));
        assertThat(limiter.allows(site, 3L)).isFalse();

        assertThat(limiterAt(MINUTE.plusSeconds(60)).allows(site, 3L)).isTrue();
    }

    @Test
    void expiresItsBucketsSoRedisDoesNotGrowForever() {
        UUID site = UUID.randomUUID();
        limiterAt(MINUTE).allows(site, 4L);

        String key = "rate:" + MINUTE.getEpochSecond() / 60 + ":" + site + ":4";
        assertThat(redis.getExpire(key)).isPositive();
    }
}
