package world.wholestory.api.analytics.application;

import java.util.List;
import java.util.UUID;

/** Read-side port over processor's analytics views. SQL-first: no aggregates, no ORM (D-042). */
public interface StatsQueries {

    SummaryStats summary(UUID siteId, DateRange range, List<Filter> filters);

    List<TimeseriesPoint> timeseries(UUID siteId, DateRange range, Interval interval, List<Filter> filters);

    List<BreakdownEntry> breakdown(UUID siteId, DateRange range, Dimension dimension, int limit, List<Filter> filters);
}
