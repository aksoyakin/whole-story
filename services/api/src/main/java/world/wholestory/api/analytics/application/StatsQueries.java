package world.wholestory.api.analytics.application;

import java.util.List;
import java.util.UUID;

/** Read-side port over processor's analytics views. SQL-first: no aggregates, no ORM (D-042). */
public interface StatsQueries {

    SummaryStats summary(UUID siteId, DateRange range, List<Filter> filters);

    List<TimeseriesPoint> timeseries(UUID siteId, DateRange range, Interval interval, List<Filter> filters);

    List<BreakdownEntry> breakdown(UUID siteId, DateRange range, Dimension dimension, int limit, List<Filter> filters);

    /**
     * How each of a site's goals did. The definitions come from Site Management, which owns them; this side only
     * evaluates them against the events it already has.
     *
     * @return one entry per definition, in the order given, including the goals nobody completed
     */
    List<GoalConversion> goals(UUID siteId, DateRange range, List<GoalDefinition> goals, List<Filter> filters);
}
