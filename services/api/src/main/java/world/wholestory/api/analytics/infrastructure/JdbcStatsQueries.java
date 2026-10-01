package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import world.wholestory.api.analytics.application.BreakdownEntry;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.Dimension;
import world.wholestory.api.analytics.application.Filter;
import world.wholestory.api.analytics.application.Interval;
import world.wholestory.api.analytics.application.StatsQueries;
import world.wholestory.api.analytics.application.SummaryStats;
import world.wholestory.api.analytics.application.TimeseriesPoint;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hand-written SQL over the views processor publishes (D-012, D-042).
 * <p>
 * Which table answers a question follows from whether its metric can be summed (D-030): pageviews come from the
 * hourly rollup, while visitors are counted exactly from sessions, because the same person seen in two hours is
 * one visitor and no sum knows that.
 * <p>
 * A filter changes that. The rollup holds no dimension beyond the page, so as soon as anything is filtered the
 * pageviews have to be counted from the events instead. Everything a session knows stays on sessions, because
 * the geo and device fields are denormalised onto both tables for exactly this.
 */
@Repository
@RequiredArgsConstructor
class JdbcStatsQueries implements StatsQueries {

    private static final String SESSIONS = """
            from analytics.api_sessions s
            where s.site_id = :siteId and s.started_at >= :start and s.started_at < :end%s
            """;
    private static final String EVENTS = """
            from analytics.api_events e
            where e.site_id = :siteId and e.timestamp >= :start and e.timestamp < :end%s
            """;

    private static final String SUMMARY = """
            select v.visitors, v.visits, v.bounce_rate, v.avg_duration, p.pageviews
            from (select count(distinct s.visitor_hash)          as visitors,
                         count(*)                                as visits,
                         coalesce(avg(s.is_bounce::int), 0)      as bounce_rate,
                         coalesce(avg(s.duration_seconds), 0)    as avg_duration
                  %s) v
            cross join (%s) p
            """;
    private static final String PAGEVIEWS_FROM_ROLLUP = """
            select coalesce(sum(pageviews), 0) as pageviews
            from analytics.api_page_hourly
            where site_id = :siteId and hour >= :start and hour < :end
            """;
    private static final String PAGEVIEWS_FROM_EVENTS = """
            select count(*) filter (where e.name = 'pageview') as pageviews
            %s
            """;

    /**
     * Days are cut in the site's timezone, which is the one place the rollup's hourly grain shows: in a zone
     * whose offset is not a whole number of hours the boundary lands inside an hour (ADR 0020). Visitors come
     * from sessions and are exact in every zone.
     */
    private static final String TIMESERIES_BY_DAY = """
            with buckets as (select generate_series(:fromDate::date, :toDate::date, interval '1 day')::date as bucket),
                 visitors as (select (s.started_at at time zone :zone)::date as bucket,
                                     count(distinct s.visitor_hash)          as visitors
                              %s
                              group by 1),
                 views as (%s)
            select (b.bucket::timestamp at time zone :zone) as bucket_at,
                   coalesce(v.visitors, 0)                  as visitors,
                   coalesce(w.pageviews, 0)                 as pageviews
            from buckets b
                     left join visitors v on v.bucket = b.bucket
                     left join views w on w.bucket = b.bucket
            order by b.bucket
            """;

    /**
     * An hour is an hour everywhere, so hour buckets need no timezone conversion — but they do need to be
     * anchored where the range begins. The site's day starts at its own local midnight, which in a zone whose
     * offset is not a whole number of hours is a moment like 18:30Z, so the buckets sit at half past. Truncating
     * the data to whole hours instead would match none of them and the whole series would come back as zeroes.
     * {@code date_bin} puts every moment of the range into exactly one of these buckets, the boundaries included.
     */
    private static final String TIMESERIES_BY_HOUR = """
            with buckets as (select generate_series(:start, :end - interval '1 hour', interval '1 hour') as bucket_at),
                 visitors as (select date_bin(interval '1 hour', s.started_at, :start) as bucket_at,
                                     count(distinct s.visitor_hash)                    as visitors
                              %s
                              group by 1),
                 views as (%s)
            select b.bucket_at, coalesce(v.visitors, 0) as visitors, coalesce(w.pageviews, 0) as pageviews
            from buckets b
                     left join visitors v on v.bucket_at = b.bucket_at
                     left join views w on w.bucket_at = b.bucket_at
            order by b.bucket_at
            """;

    private static final String DAILY_VIEWS_FROM_ROLLUP = """
            select (hour at time zone :zone)::date as bucket, sum(pageviews) as pageviews
            from analytics.api_page_hourly
            where site_id = :siteId and hour >= :start and hour < :end
            group by 1
            """;
    private static final String DAILY_VIEWS_FROM_EVENTS = """
            select (e.timestamp at time zone :zone)::date           as bucket,
                   count(*) filter (where e.name = 'pageview')      as pageviews
            %s
            group by 1
            """;
    /**
     * The rollup's own grain is the whole UTC hour, so in a zone offset by half an hour its rows do not line up
     * with the buckets and each one is binned into the bucket it starts in. That is the rounding this rollup
     * carries by design (ADR 0020); the summary's pageview count drops the same partial hour, so the two agree.
     */
    private static final String HOURLY_VIEWS_FROM_ROLLUP = """
            select date_bin(interval '1 hour', hour, :start) as bucket_at, sum(pageviews) as pageviews
            from analytics.api_page_hourly
            where site_id = :siteId and hour >= :start and hour < :end
            group by 1
            """;
    private static final String HOURLY_VIEWS_FROM_EVENTS = """
            select date_bin(interval '1 hour', e.timestamp, :start) as bucket_at,
                   count(*) filter (where e.name = 'pageview')      as pageviews
            %s
            group by 1
            """;

    /** One session row carries visitors, visits and pageviews together, so these need a single pass. */
    private static final String BREAKDOWN_BY_SESSION = """
            select coalesce(s.%s::text, '')        as key,
                   %s                              as label,
                   count(distinct s.visitor_hash)  as visitors,
                   count(*)                        as visits,
                   coalesce(sum(s.pageviews), 0)   as pageviews
            %s
            group by 1, 2
            order by visitors desc, key
            limit :limit
            """;

    /** A page belongs to a pageview, not to a visit, so this one is grouped from the events. */
    private static final String BREAKDOWN_BY_EVENT = """
            select coalesce(e.%s::text, '')                     as key,
                   %s                                            as label,
                   count(distinct e.visitor_hash)                as visitors,
                   count(distinct e.session_id)                  as visits,
                   count(*) filter (where e.name = 'pageview')   as pageviews
            %s
            group by 1, 2
            order by visitors desc, key
            limit :limit
            """;

    private final JdbcClient jdbc;

    @Override
    public SummaryStats summary(UUID siteId, DateRange range, List<Filter> filters) {
        FilterClauses clauses = FilterClauses.of(filters);
        String pageviews = clauses.any()
                ? PAGEVIEWS_FROM_EVENTS.formatted(events(clauses))
                : PAGEVIEWS_FROM_ROLLUP;
        return bind(SUMMARY.formatted(sessions(clauses), pageviews), siteId, range, clauses)
                .query((rs, row) -> new SummaryStats(
                        rs.getLong("visitors"),
                        rs.getLong("visits"),
                        rs.getLong("pageviews"),
                        rs.getDouble("bounce_rate"),
                        rs.getDouble("avg_duration")))
                .single();
    }

    @Override
    public List<TimeseriesPoint> timeseries(UUID siteId, DateRange range, Interval interval, List<Filter> filters) {
        FilterClauses clauses = FilterClauses.of(filters);
        boolean hourly = interval == Interval.HOUR;
        String views = clauses.any()
                ? (hourly ? HOURLY_VIEWS_FROM_EVENTS : DAILY_VIEWS_FROM_EVENTS).formatted(events(clauses))
                : (hourly ? HOURLY_VIEWS_FROM_ROLLUP : DAILY_VIEWS_FROM_ROLLUP);
        String sql = (hourly ? TIMESERIES_BY_HOUR : TIMESERIES_BY_DAY).formatted(sessions(clauses), views);
        return bind(sql, siteId, range, clauses)
                .param("zone", range.zone().getId())
                .param("fromDate", range.from())
                .param("toDate", range.to())
                .query((rs, row) -> new TimeseriesPoint(
                        rs.getObject("bucket_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("visitors"),
                        rs.getLong("pageviews")))
                .list();
    }

    @Override
    public List<BreakdownEntry> breakdown(UUID siteId, DateRange range, Dimension dimension, int limit,
                                          List<Filter> filters) {
        FilterClauses clauses = FilterClauses.of(filters);
        boolean fromEvents = dimension.source() == Dimension.Source.EVENTS;
        String alias = fromEvents ? "e." : "s.";
        String label = dimension.labelColumn() == null ? "null::text" : alias + dimension.labelColumn();
        String sql = (fromEvents ? BREAKDOWN_BY_EVENT : BREAKDOWN_BY_SESSION)
                .formatted(dimension.keyColumn(), label, fromEvents ? events(clauses) : sessions(clauses));
        return bind(sql, siteId, range, clauses)
                .param("limit", limit)
                .query((rs, row) -> {
                    String key = rs.getString("key");
                    String name = rs.getString("label");
                    // Always a label: the client should not have to decide what to print when there is none.
                    return new BreakdownEntry(
                            key,
                            name == null || name.isBlank() ? key : name,
                            rs.getLong("visitors"),
                            rs.getLong("visits"),
                            rs.getLong("pageviews"));
                })
                .list();
    }

    private static String sessions(FilterClauses clauses) {
        return SESSIONS.formatted(clauses.onSessions());
    }

    private static String events(FilterClauses clauses) {
        return EVENTS.formatted(clauses.onEvents());
    }

    private JdbcClient.StatementSpec bind(String sql, UUID siteId, DateRange range, FilterClauses clauses) {
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("siteId", siteId)
                .param("start", at(range.start()))
                .param("end", at(range.endExclusive()));
        for (Map.Entry<String, Object> parameter : clauses.parameters().entrySet()) {
            statement = statement.param(parameter.getKey(), parameter.getValue());
        }
        return statement;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
