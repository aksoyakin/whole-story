package world.wholestory.ingest.privacy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import world.wholestory.ingest.TestcontainersConfiguration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DailySaltProviderIntegrationTest {

    // Far future, and a different day per test: salts are shared state in Redis and setIfAbsent must not
    // reset the deadline another test established.
    private static final LocalDate EXPIRY_DAY = LocalDate.parse("2126-09-28");
    private static final LocalDate QUIET_DAY = LocalDate.parse("2126-10-05");
    private static final LocalDate SHARED_DAY = LocalDate.parse("2126-11-12");

    @Autowired
    StringRedisTemplate redis;

    @Test
    void expiresTheSaltWhenTheRotationGraceOfTheNextDayEnds() {
        // Created late in the day: a relative time-to-live would keep it alive far past the grace period.
        Instant lateInTheDay = Instant.parse("2126-09-28T22:00:00Z");
        DailySaltProvider provider = new DailySaltProvider(redis, Clock.fixed(lateInTheDay, ZoneOffset.UTC));

        provider.saltFor(EXPIRY_DAY);

        Long ttlSeconds = redis.getExpire("salt:" + EXPIRY_DAY, TimeUnit.SECONDS);
        Duration expected = Duration.between(lateInTheDay, DailySaltProvider.expiryOf(EXPIRY_DAY));
        assertThat(expected).isEqualTo(Duration.ofHours(2).plusMinutes(30));
        assertThat(ttlSeconds).isCloseTo(expected.toSeconds(), org.assertj.core.data.Offset.offset(5L));
    }

    @Test
    void neverCreatesASaltForADayThatHadNoTraffic() {
        DailySaltProvider provider = new DailySaltProvider(redis, Clock.systemUTC());

        assertThat(provider.existingSaltFor(QUIET_DAY)).isEmpty();
        assertThat(redis.hasKey("salt:" + QUIET_DAY)).isFalse();
    }

    @Test
    void readsBackTheSaltAnotherInstanceStored() {
        Clock clock = Clock.fixed(Instant.parse("2126-11-12T10:00:00Z"), ZoneOffset.UTC);
        byte[] stored = new DailySaltProvider(redis, clock).saltFor(SHARED_DAY);

        // A second instance has an empty cache and must get the same salt from Redis.
        assertThat(new DailySaltProvider(redis, clock).existingSaltFor(SHARED_DAY)).hasValue(stored);
    }
}
