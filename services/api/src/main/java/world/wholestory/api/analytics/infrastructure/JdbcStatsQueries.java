package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import world.wholestory.api.analytics.application.BreakdownEntry;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.Dimension;
import world.wholestory.api.analytics.application.Interval;
import world.wholestory.api.analytics.application.StatsQueries;
import world.wholestory.api.analytics.application.SummaryStats;
import world.wholestory.api.analytics.application.TimeseriesPoint;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Hand-written SQL over the views processor publishes (D-012, D-042).
 * <p>
 * Which table answers a question follows from whether the metric can be summed (D-030): pageviews come from the
 * hourly rollup, while visitors are counted exactly from sessions, because the same person seen in two hours is
 * one visitor and no sum can know that.
 */
@Repository
@RequiredArgsConstructor
class JdbcStatsQueries implements StatsQueries {

    private static final String SUMMARY = """
            select s.visitors, s.visits, s.bounce_rate, s.avg_duration, p.pageviews
            from (select count(distinct visitor_hash)       as visitors,
                         count(*)                          as visits,
                         coalesce(avg(is_bounce::int), 0)   as bounce_rate,
                         coalesce(avg(duration_seconds), 0) as avg_duration
                  from analytics.api_sessions
                  where site_id = :siteId and started_at >= :start and started_at < :end) s
            cross join (select coalesce(sum(pageviews), 0) as pageviews
                        from analytics.api_page_hourly
                        where site_id = :siteId and hour >= :start and hour < :end) p
            """;

    /**
     * Days are cut in the site's timezone, which is the one place a rollup's hourly grain shows: in a zone whose
     * offset is not a whole number of hours the boundary lands inside an hour (ADR 0020). Visitors come from
     * sessions and are exact in every zone.
     */
    private static final String TIMESERIES_BY_DAY = """
            with buckets as (select generate_series(:fromDate::date, :toDate::date, interval '1 day')::date as bucket),
                 visitors as (select (started_at at time zone :zone)::date as bucket,
                                     count(distinct visitor_hash)          as visitors
                              from analytics.api_sessions
                              where site_id = :siteId and started_at >= :start and started_at < :end
                              group by 1),
                 views as (select (hour at time zone :zone)::date as bucket, sum(pageviews) as pageviews
                           from analytics.api_page_hourly
                           where site_id = :siteId and hour >= :start and hour < :end
                           group by 1)
            select (b.bucket::timestamp at time zone :zone) as bucket_at,
                   coalesce(v.visitors, 0)                  as visitors,
                   coalesce(w.pageviews, 0)                 as pageviews
            from buckets b
                     left join visitors v on v.bucket = b.bucket
                     left join views w on w.bucket = b.bucket
            order by b.bucket
            """;

    /** Hour buckets need no timezone: an hour is an hour everywhere, and the client labels them. */
    private static final String TIMESERIES_BY_HOUR = """
            with buckets as (select generate_series(:start, :end - interval '1 hour', interval '1 hour') as bucket_at),
                 visitors as (select date_trunc('hour', started_at) as bucket_at,
                                     count(distinct visitor_hash)   as visitors
                              from analytics.api_sessions
                              where site_id = :siteId and started_at >= :start and started_at < :end
                              group by 1),
                 views as (select hour as bucket_at, sum(pageviews) as pageviews
                           from analytics.api_page_hourly
                           where site_id = :siteId and hour >= :start and hour < :end
                           group by 1)
            select b.bucket_at, coalesce(v.visitors, 0) as visitors, coalesce(w.pageviews, 0) as pageviews
            from buckets b
                     left join visitors v on v.bucket_at = b.bucket_at
                     left join views w on w.bucket_at = b.bucket_at
            order by b.bucket_at
            """;

    /** One session row carries visitors, visits and pageviews together, so these need a single pass. */
    private static final String BREAKDOWN_BY_SESSION = """
            select coalesce(%s::text, '')      as key,
                   %s                          as label,
                   count(distinct visitor_hash) as visitors,
                   count(*)                     as visits,
                   coalesce(sum(pageviews), 0)  as pageviews
            from analytics.api_sessions
            where site_id = :siteId and started_at >= :start and started_at < :end
            group by 1, 2
            order by visitors desc, key
            limit :limit
            """;

    /** A page belongs to a pageview rather than to a visit, so this one is grouped from the events. */
    private static final String BREAKDOWN_BY_EVENT = """
            select coalesce(%s::text, '')                   as key,
                   %s                                        as label,
                   count(distinct visitor_hash)              as visitors,
                   count(distinct session_id)                as visits,
                   count(*) filter (where name = 'pageview') as pageviews
            from analytics.api_events
            where site_id = :siteId and timestamp >= :start and timestamp < :end
            group by 1, 2
            order by visitors desc, key
            limit :limit
            """;

    private final JdbcClient jdbc;

    @Override
    public SummaryStats summary(UUID siteId, DateRange range) {
        return jdbc.sql(SUMMARY)
                .param("siteId", siteId)
                .param("start", at(range.start()))
                .param("end", at(range.endExclusive()))
                .query((rs, row) -> new SummaryStats(
                        rs.getLong("visitors"),
                        rs.getLong("visits"),
                        rs.getLong("pageviews"),
                        rs.getDouble("bounce_rate"),
                        rs.getDouble("avg_duration")))
                .single();
    }

    @Override
    public List<TimeseriesPoint> timeseries(UUID siteId, DateRange range, Interval interval) {
        return jdbc.sql(interval == Interval.HOUR ? TIMESERIES_BY_HOUR : TIMESERIES_BY_DAY)
                .param("siteId", siteId)
                .param("start", at(range.start()))
                .param("end", at(range.endExclusive()))
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
    public List<BreakdownEntry> breakdown(UUID siteId, DateRange range, Dimension dimension, int limit) {
        // The column names come from the enum, never from the request, so nothing a caller sends shapes the SQL.
        String template = dimension.source() == Dimension.Source.EVENTS ? BREAKDOWN_BY_EVENT : BREAKDOWN_BY_SESSION;
        String labelColumn = dimension.labelColumn() == null ? "null::text" : dimension.labelColumn();
        return jdbc.sql(template.formatted(dimension.keyColumn(), labelColumn))
                .param("siteId", siteId)
                .param("start", at(range.start()))
                .param("end", at(range.endExclusive()))
                .param("limit", limit)
                .query((rs, row) -> {
                    String key = rs.getString("key");
                    String label = rs.getString("label");
                    // Always a label: the client should not have to decide what to print when there is none.
                    return new BreakdownEntry(
                            key,
                            label == null || label.isBlank() ? key : label,
                            rs.getLong("visitors"),
                            rs.getLong("visits"),
                            rs.getLong("pageviews"));
                })
                .list();
    }

    private static OffsetDateTime at(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
