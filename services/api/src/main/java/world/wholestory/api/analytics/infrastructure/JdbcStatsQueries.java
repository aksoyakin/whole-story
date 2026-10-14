package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import world.wholestory.api.analytics.application.BreakdownEntry;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.Dimension;
import world.wholestory.api.analytics.application.Filter;
import world.wholestory.api.analytics.application.GoalConversion;
import world.wholestory.api.analytics.application.GoalDefinition;
import world.wholestory.api.analytics.application.Interval;
import world.wholestory.api.analytics.application.StatsQueries;
import world.wholestory.api.analytics.application.SummaryStats;
import world.wholestory.api.analytics.application.TimeseriesPoint;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
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

    /**
     * The same two questions, answered from the daily rollup wherever it reaches.
     * <p>
     * Whole UTC days come from {@code visitor_daily} and the partial days at the edges are still counted from
     * the sessions, which is what keeps the figure exact: visitors add up across days only because the salt
     * rotates daily (D-159), and a site's day does not begin on a UTC midnight (ADR 0020). The three parts
     * tile the range with no gap and no overlap — {@link Coverage} works that out and is tested on its own,
     * because a gap here loses visitors and an overlap doubles them, and both just look like a number.
     * <p>
     * Only used when nothing is filtered. A filter on one dimension cannot be answered from another's rollup
     * without pre-aggregating every combination, so a filtered report reads the sessions as it always did.
     */
    private static final String SUMMARY_FROM_ROLLUP = """
            select v.visitors,
                   v.visits,
                   case when v.visits = 0 then 0 else v.bounces::numeric / v.visits end      as bounce_rate,
                   case when v.visits = 0 then 0 else v.duration_sum::numeric / v.visits end as avg_duration,
                   p.pageviews
            from (select coalesce(sum(visitors), 0)     as visitors,
                         coalesce(sum(visits), 0)       as visits,
                         coalesce(sum(bounces), 0)      as bounces,
                         coalesce(sum(duration_sum), 0) as duration_sum
                  from (select visitors, visits, bounces, duration_sum
                        from analytics.api_visitor_daily
                        where site_id = :siteId and dimension = 'TOTAL'
                          and utc_day >= :rollupFrom and utc_day < :rollupTo
                        union all
                        select count(distinct s.visitor_hash), count(*),
                               count(*) filter (where s.is_bounce), coalesce(sum(s.duration_seconds), 0)
                        from analytics.api_sessions s
                        where s.site_id = :siteId and s.started_at >= :headFrom and s.started_at < :headTo
                        union all
                        select count(distinct s.visitor_hash), count(*),
                               count(*) filter (where s.is_bounce), coalesce(sum(s.duration_seconds), 0)
                        from analytics.api_sessions s
                        where s.site_id = :siteId and s.started_at >= :tailFrom and s.started_at < :tailTo) parts) v
            cross join (%s) p
            """;

    private static final String BREAKDOWN_FROM_ROLLUP = """
            select key,
                   max(label)                 as label,
                   coalesce(sum(visitors), 0) as visitors
            from (select value as key, label, visitors
                  from analytics.api_visitor_daily
                  where site_id = :siteId and dimension = :dimension
                    and utc_day >= :rollupFrom and utc_day < :rollupTo
                  union all
                  %1$s
                  union all
                  %2$s) parts
            group by key
            order by visitors desc, key
            limit :limit
            """;

    /** One partial day at an edge of the range, for a dimension the sessions carry. */
    private static final String EDGE_FROM_SESSIONS = """
            select coalesce(s.%1$s::text, ''), %2$s, count(distinct s.visitor_hash)
                  from analytics.api_sessions s
                  where s.site_id = :siteId and s.started_at >= :%3$sFrom and s.started_at < :%3$sTo
                  group by 1, 2""";

    /**
     * The same edge, for a dimension only the events carry.
     * <p>
     * A page belongs to a pageview rather than to a visit, so the live remainder has to be grouped from the
     * events exactly as an unrolled breakdown would be. Getting this wrong would not fail: it would quietly
     * count the wrong rows at the two ends of every range.
     */
    private static final String EDGE_FROM_EVENTS = """
            select coalesce(e.%1$s::text, ''), %2$s, count(distinct e.visitor_hash)
                  from analytics.api_events e
                  where e.site_id = :siteId and e.timestamp >= :%3$sFrom and e.timestamp < :%3$sTo
                  group by 1, 2""";

    private static final String BREAKDOWN_BY_SESSION = """
            select coalesce(s.%s::text, '')       as key,
                   %s                             as label,
                   count(distinct s.visitor_hash) as visitors
            %s
            group by 1, 2
            order by visitors desc, key
            limit :limit
            """;

    /**
     * Every goal in one pass over the events, two expressions each.
     * <p>
     * The counting comes from {@code api_events} rather than from the rollups, which is a departure from the
     * first sketch of the data model and deliberate: a conversion rate needs the visitors who converted, which
     * is a distinct count and therefore not a thing a rollup can hold (D-030). The rollup could still have
     * supplied the completion total, but that would mean two sources for one card and a way for them to
     * disagree — and as soon as anything is filtered the rollup cannot answer at all (D-103).
     * <p>
     * The denominator is the visitors of the period, the same number the summary tile shows, so the page agrees
     * with itself.
     */
    private static final String GOAL_CONVERSIONS = """
            with visitors as (%s),
                 conversions as (select %s
                                 %s)
            select v.total, c.*
            from visitors v
                     cross join conversions c
            """;

    private static final String VISITORS_FROM_SESSIONS = """
            select count(distinct s.visitor_hash) as total
            %s""";

    /**
     * The denominator, taken from the rollup wherever it reaches.
     * <p>
     * It is the same number the summary tile shows, counted the same way for the same reason — which is the
     * point: the page has to agree with itself, so the two must not drift into different definitions. Measured
     * over twelve months it was 1,934 ms from the sessions and effectively nothing from here, which made it a
     * seventh of what the goals card cost for no change in the answer.
     */
    private static final String VISITORS_FROM_ROLLUP = """
            select coalesce(sum(visitors), 0) as total
            from (select visitors
                  from analytics.api_visitor_daily
                  where site_id = :siteId and dimension = 'TOTAL'
                    and utc_day >= :rollupFrom and utc_day < :rollupTo
                  union all
                  select count(distinct s.visitor_hash)
                  from analytics.api_sessions s
                  where s.site_id = :siteId and s.started_at >= :headFrom and s.started_at < :headTo
                  union all
                  select count(distinct s.visitor_hash)
                  from analytics.api_sessions s
                  where s.site_id = :siteId and s.started_at >= :tailFrom and s.started_at < :tailTo) parts
            """;

    /** A page belongs to a pageview, not to a visit, so this one is grouped from the events. */
    private static final String BREAKDOWN_BY_EVENT = """
            select coalesce(e.%s::text, '')       as key,
                   %s                             as label,
                   count(distinct e.visitor_hash) as visitors
            %s
            group by 1, 2
            order by visitors desc, key
            limit :limit
            """;

    private final JdbcClient jdbc;

    @Override
    public SummaryStats summary(UUID siteId, DateRange range, List<Filter> filters) {
        FilterClauses clauses = FilterClauses.of(filters);
        if (!clauses.any()) {
            Coverage coverage = coverageOf(range);
            if (coverage.usesRollup()) {
                return bindCoverage(bind(SUMMARY_FROM_ROLLUP.formatted(PAGEVIEWS_FROM_ROLLUP),
                                siteId, range, clauses), coverage)
                        .query((rs, row) -> summaryOf(rs))
                        .single();
            }
        }
        String pageviews = clauses.any()
                ? PAGEVIEWS_FROM_EVENTS.formatted(events(clauses))
                : PAGEVIEWS_FROM_ROLLUP;
        return bind(SUMMARY.formatted(sessions(clauses), pageviews), siteId, range, clauses)
                .query((rs, row) -> summaryOf(rs))
                .single();
    }

    private static SummaryStats summaryOf(ResultSet rs) throws SQLException {
        return new SummaryStats(
                rs.getLong("visitors"),
                rs.getLong("visits"),
                rs.getLong("pageviews"),
                rs.getDouble("bounce_rate"),
                rs.getDouble("avg_duration"));
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
        if (dimension.rolledUp() && !clauses.any()) {
            Coverage coverage = coverageOf(range);
            if (coverage.usesRollup()) {
                String edge = fromEvents ? EDGE_FROM_EVENTS : EDGE_FROM_SESSIONS;
                String label = labelExpression(dimension, fromEvents ? "e." : "s.");
                String sql = BREAKDOWN_FROM_ROLLUP.formatted(
                        edge.formatted(dimension.keyColumn(), label, "head"),
                        edge.formatted(dimension.keyColumn(), label, "tail"));
                return bindCoverage(bind(sql, siteId, range, clauses), coverage)
                        .param("dimension", dimension.name())
                        .param("limit", limit)
                        .query((rs, row) -> entryOf(rs))
                        .list();
            }
        }
        String sql = (fromEvents ? BREAKDOWN_BY_EVENT : BREAKDOWN_BY_SESSION).formatted(
                dimension.keyColumn(),
                labelExpression(dimension, fromEvents ? "e." : "s."),
                fromEvents ? events(clauses) : sessions(clauses));
        return bind(sql, siteId, range, clauses)
                .param("limit", limit)
                .query((rs, row) -> entryOf(rs))
                .list();
    }

    /** Column names come from the enum, never from the request, so nothing a caller sends can shape the SQL. */
    private static String labelExpression(Dimension dimension, String alias) {
        return dimension.labelColumn() == null ? "null::text" : alias + dimension.labelColumn();
    }

    /** Always a label: the client should not have to decide what to print when there is none. */
    private static BreakdownEntry entryOf(ResultSet rs) throws SQLException {
        String key = rs.getString("key");
        String label = rs.getString("label");
        return new BreakdownEntry(key, label == null || label.isBlank() ? key : label, rs.getLong("visitors"));
    }

    /**
     * How far the rollup reaches. One small read per report; an absent row or a null watermark means it has
     * never run, and then everything is counted live exactly as before.
     */
    private Coverage coverageOf(DateRange range) {
        LocalDate covered = jdbc.sql("select covered_through from analytics.api_visitor_daily_watermark")
                .query(LocalDate.class)
                .optional()
                .orElse(null);
        return Coverage.of(range.start(), range.endExclusive(), covered);
    }

    private static JdbcClient.StatementSpec bindCoverage(JdbcClient.StatementSpec statement, Coverage coverage) {
        return statement
                .param("rollupFrom", coverage.rollupFrom())
                .param("rollupTo", coverage.rollupTo())
                .param("headFrom", at(coverage.headFrom()))
                .param("headTo", at(coverage.headTo()))
                .param("tailFrom", at(coverage.tailFrom()))
                .param("tailTo", at(coverage.tailTo()));
    }

    @Override
    public List<GoalConversion> goals(UUID siteId, DateRange range, List<GoalDefinition> goals,
                                      List<Filter> filters) {
        if (goals.isEmpty()) {
            return List.of();
        }
        FilterClauses clauses = FilterClauses.of(filters);
        StringJoiner expressions = new StringJoiner(",\n                                        ");
        Map<String, Object> targets = new LinkedHashMap<>();
        for (int i = 0; i < goals.size(); i++) {
            GoalDefinition goal = goals.get(i);
            boolean page = goal.kind() == GoalDefinition.Kind.PAGE;
            String parameter = "g" + i;
            targets.put(parameter, page ? PagePattern.toLikePattern(goal.target()) : goal.target());
            // Generated names and a bound value: the goal's own text never becomes part of the statement.
            String match = page
                    ? "(e.name = 'pageview' and e.pathname like :%s escape '\\')".formatted(parameter)
                    : "e.name = :%s".formatted(parameter);
            expressions.add("count(distinct case when %s then e.visitor_hash end) as v%d".formatted(match, i));
            expressions.add("count(*) filter (where %s) as c%d".formatted(match, i));
        }

        // Only the denominator can come from the rollup. The conversions cannot: a goal is defined by whoever
        // owns the site, at any time, and reports history from before it existed (D-122), so there is nothing
        // to have pre-aggregated.
        Coverage coverage = clauses.any() ? null : coverageOf(range);
        boolean fromRollup = coverage != null && coverage.usesRollup();
        String denominator = fromRollup ? VISITORS_FROM_ROLLUP : VISITORS_FROM_SESSIONS.formatted(sessions(clauses));

        String sql = GOAL_CONVERSIONS.formatted(denominator, expressions, events(clauses));
        JdbcClient.StatementSpec statement = bind(sql, siteId, range, clauses);
        if (fromRollup) {
            statement = bindCoverage(statement, coverage);
        }
        for (Map.Entry<String, Object> target : targets.entrySet()) {
            statement = statement.param(target.getKey(), target.getValue());
        }
        return statement.query((rs, row) -> read(rs, goals)).single();
    }

    /** One row holds every goal's pair of numbers, plus the visitors they are a share of. */
    private static List<GoalConversion> read(ResultSet rs, List<GoalDefinition> goals) throws SQLException {
        long total = rs.getLong("total");
        List<GoalConversion> conversions = new ArrayList<>(goals.size());
        for (int i = 0; i < goals.size(); i++) {
            long visitors = rs.getLong("v" + i);
            conversions.add(new GoalConversion(
                    goals.get(i).goalId(),
                    visitors,
                    rs.getLong("c" + i),
                    total == 0 ? 0 : (double) visitors / total));
        }
        return conversions;
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
