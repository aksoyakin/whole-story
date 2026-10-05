package world.wholestory.processor.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisZSetCommands.ZAddArgs;
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

    /**
     * {@code events} are the ones the batch actually stored, already past the bot filter.
     * <p>
     * The score is the event's own timestamp rather than the moment this runs, and that is what makes a replay
     * harmless: a re-read of the topic scores every member where it belongs in the past, and the trim below
     * takes it straight back out. Scoring with the processing time instead put the whole history onto the page
     * as people who were there right now — measured, a rewind took a cleared counter to 80,000 — and it also
     * made a consumer that had fallen behind report visitors from minutes ago as present.
     * <p>
     * Written with {@code GT} so a score can only move forward. Without it a late or replayed event would drag
     * a visitor who really is here back into the past, and the trim would then remove somebody present.
     */
    public void record(List<SessionizedEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        // The window belongs to the clock, not to the data: what is kept is the last ten minutes of real time.
        double dropBefore = (double) clock.instant().getEpochSecond() - KEPT.toSeconds();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            for (SessionizedEvent event : events) {
                byte[] key = RealtimeVisitorKeys.forSite(event.event().siteId()).getBytes(StandardCharsets.UTF_8);
                connection.zSetCommands()
                        .zAdd(key, event.event().timestamp().getEpochSecond(), member(event), ZAddArgs.empty().gt());
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
