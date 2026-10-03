package world.wholestory.processor.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import world.wholestory.contracts.RealtimeVisitorKeys;
import world.wholestory.processor.sessionization.SessionizedEvent;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Remembers who was just here, so the dashboard can say how many people are on a site right now.
 * <p>
 * It runs in the processor rather than in ingest because ingest has not filtered the crawlers yet (D-064):
 * counting there would put Googlebot into the most visible number on the page.
 * <p>
 * Redis is the right place and losing it costs nothing but this number — unlike the tracked-domain list, which
 * had to stop being a cache that must never be cold (ADR 0019). Nothing is rebuilt and nothing is missed: the
 * count simply starts again from the next event.
 */
@Component
@RequiredArgsConstructor
public class RealtimeVisitorRecorder {

    /**
     * How long a written entry is kept. Deliberately **longer** than the window api counts over, which is five
     * minutes: trimming only bounds memory, while what the answer covers is the reader's decision. Were the two
     * equal, a trim arriving a moment early would quietly shorten somebody's count; with twice the room it
     * cannot. Whoever changes the window on the reading side has to keep this above it.
     */
    private static final Duration KEPT = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final Clock clock;

    /** {@code events} have already been through the bot filter; replays are harmless, since a visitor is a member. */
    public void record(List<SessionizedEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        long now = clock.instant().getEpochSecond();
        double dropBefore = (double) now - KEPT.toSeconds();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            for (SessionizedEvent event : events) {
                byte[] key = RealtimeVisitorKeys.forSite(event.event().siteId()).getBytes(StandardCharsets.UTF_8);
                connection.zSetCommands().zAdd(key, now, member(event));
                // Bounded on write rather than swept later: a busy site would otherwise grow a set nobody trims.
                connection.zSetCommands().zRemRangeByScore(key, Double.NEGATIVE_INFINITY, dropBefore);
                // A site that stops receiving events should not leave a key behind for ever.
                connection.keyCommands().expire(key, KEPT.toSeconds());
            }
            return null;
        });
    }

    private static byte[] member(SessionizedEvent event) {
        return Long.toString(event.event().visitorHash()).getBytes(StandardCharsets.UTF_8);
    }
}
