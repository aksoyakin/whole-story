package world.wholestory.processor.rollup;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.processor.sessionization.SessionizedEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static world.wholestory.processor.persistence.MultiRowInsert.utc;
import static world.wholestory.processor.persistence.MultiRowInsert.values;

/** Hourly UTC rollups of additive metrics (D-020, D-030). */
@Component
@RequiredArgsConstructor
public class RollupWriter {

    private static final String PAGES = """
            insert into analytics.page_hourly (site_id, hour, pathname, pageviews)
            values %s
            on conflict (site_id, hour, pathname) do update set pageviews = page_hourly.pageviews + excluded.pageviews
            """;
    private static final String CUSTOM_EVENTS = """
            insert into analytics.custom_event_hourly (site_id, hour, event_name, count)
            values %s
            on conflict (site_id, hour, event_name) do update set count = custom_event_hourly.count + excluded.count
            """;

    private final JdbcClient jdbc;

    /** {@code events} must contain only newly inserted events, otherwise replays would double count (D-032). */
    public void apply(List<SessionizedEvent> events) {
        Map<Bucket, Long> pages = new LinkedHashMap<>();
        Map<Bucket, Long> customEvents = new LinkedHashMap<>();
        for (SessionizedEvent sessionized : events) {
            RawEventV1 e = sessionized.event();
            Instant hour = e.timestamp().truncatedTo(ChronoUnit.HOURS);
            if (e.representsPageview()) {
                pages.merge(new Bucket(e.siteId(), hour, e.pathname()), 1L, Long::sum);
            } else {
                customEvents.merge(new Bucket(e.siteId(), hour, e.name()), 1L, Long::sum);
            }
        }
        upsert(PAGES, pages);
        upsert(CUSTOM_EVENTS, customEvents);
    }

    private void upsert(String sql, Map<Bucket, Long> buckets) {
        if (buckets.isEmpty()) {
            return;
        }
        List<Object> params = new ArrayList<>(buckets.size() * 4);
        buckets.forEach((bucket, count) -> {
            params.add(bucket.siteId());
            params.add(utc(bucket.hour()));
            params.add(bucket.dimension());
            params.add(count);
        });
        jdbc.sql(sql.formatted(values("(?, ?, ?, ?)", buckets.size()))).params(params).update();
    }

    private record Bucket(UUID siteId, Instant hour, String dimension) {
    }
}
