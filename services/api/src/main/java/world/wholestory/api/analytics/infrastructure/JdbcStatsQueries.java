package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import world.wholestory.api.analytics.application.AggregateStats;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.StatsQueries;

import java.time.ZoneOffset;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class JdbcStatsQueries implements StatsQueries {

    /**
     * Unique visitors are not additive, so they are counted exactly from sessions (D-031);
     * pageviews come from the hourly rollup.
     */
    private static final String AGGREGATE = """
            select
                (select count(distinct visitor_hash) from analytics.api_sessions
                  where site_id = :siteId and started_at >= :start and started_at < :end) as visitors,
                (select count(*) from analytics.api_sessions
                  where site_id = :siteId and started_at >= :start and started_at < :end) as visits,
                (select coalesce(sum(pageviews), 0) from analytics.api_page_hourly
                  where site_id = :siteId and hour >= :start and hour < :end) as pageviews
            """;

    private final JdbcClient jdbc;

    @Override
    public AggregateStats aggregate(UUID siteId, DateRange range) {
        return jdbc.sql(AGGREGATE)
                .param("siteId", siteId)
                .param("start", range.start().atOffset(ZoneOffset.UTC))
                .param("end", range.endExclusive().atOffset(ZoneOffset.UTC))
                .query((rs, row) -> new AggregateStats(rs.getLong("visitors"), rs.getLong("visits"), rs.getLong("pageviews")))
                .single();
    }
}
