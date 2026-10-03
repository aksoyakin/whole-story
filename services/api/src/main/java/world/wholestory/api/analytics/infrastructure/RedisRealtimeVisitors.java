package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import world.wholestory.api.analytics.application.RealtimeVisitors;
import world.wholestory.contracts.RealtimeVisitorKeys;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Counts the visitors the processor recorded, over a window this side owns.
 * <p>
 * The reader decides what "right now" means, which is why the window lives here and not where the entries are
 * written: the writer keeps a little more than this and trims only to bound memory, so trimming can lag
 * without ever shortening an answer.
 * <p>
 * Counting is a range over the score of one sorted set — a single {@code ZCOUNT}. Keeping a key per visitor
 * instead would make this a {@code SCAN} of the whole keyspace, which is not something a dashboard may do.
 */
@Component
@RequiredArgsConstructor
class RedisRealtimeVisitors implements RealtimeVisitors {

    /**
     * Five minutes, which is what this number can honestly mean.
     * <p>
     * The tracker sends nothing while somebody reads — a pageview, a client-side navigation and a custom event,
     * and that is all. So a visitor only counts as present for as long as the window covers the gap between
     * their events, and a short window undercounts systematically: at a minute, somebody reading an article for
     * two would be absent from the page for half their visit while the heading still called them a visitor.
     * <p>
     * The error runs the other way instead — somebody who closed the tab four minutes ago is still counted —
     * and that is the direction the label can carry. The honest fix is a heartbeat from the tracker, which is
     * not worth its bytes against a 1 KB budget (D-050) or the requests every open tab would make; Plausible
     * reaches the same five minutes from the same constraint.
     */
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Override
    public long on(UUID siteId) {
        double since = (double) clock.instant().getEpochSecond() - WINDOW.toSeconds();
        Long counted = redis.opsForZSet()
                .count(RealtimeVisitorKeys.forSite(siteId), since, Double.POSITIVE_INFINITY);
        // Redis cannot answer: a missing number beats a page that will not render.
        return counted == null ? 0L : counted;
    }
}
