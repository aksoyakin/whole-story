package world.wholestory.api.analytics.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-side port over processor's analytics views. SQL-first: no aggregates, no ORM (D-042). */
public interface StatsQueries {

    SummaryStats summary(UUID siteId, DateRange range, List<Filter> filters);

    /**
     * One measure per bucket, not both. The dashboard draws one at a time (ADR 0020) and the two cost very
     * differently, so asking for the one that will be drawn is the difference between a scan and a sum.
     */
    List<TimeseriesPoint> timeseries(UUID siteId, DateRange range, Interval interval, Measure measure,
                                     List<Filter> filters);

    List<BreakdownEntry> breakdown(UUID siteId, DateRange range, Dimension dimension, int limit, List<Filter> filters);

    /**
     * How each of a site's goals did. The definitions come from Site Management, which owns them; this side only
     * evaluates them against the events it already has.
     *
     * @return one entry per definition, in the order given, including the goals nobody completed
     */
    List<GoalConversion> goals(UUID siteId, DateRange range, List<GoalDefinition> goals, List<Filter> filters);

    /**
     * The first UTC day the raw events and sessions still reach back to, as processor publishes it after
     * dropping expired partitions (D-033).
     * <p>
     * Needed because a report is not answered from one place: the interior days of a range come from the
     * daily rollup, which is kept indefinitely, and the two partial days at its edges are counted live from
     * the data this horizon describes. A range reaching past it would be answered short by those edges and
     * say nothing about it, so it is refused instead.
     *
     * @return empty when nothing has published a horizon yet, which means no floor at all — the behaviour
     *         these endpoints had before anything was ever dropped
     */
    Optional<LocalDate> retainedFrom();
}
