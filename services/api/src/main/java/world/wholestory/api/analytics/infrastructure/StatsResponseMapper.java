package world.wholestory.api.analytics.infrastructure;

import world.wholestory.api.analytics.application.BreakdownEntry;
import world.wholestory.api.analytics.application.SummaryStats;
import world.wholestory.api.analytics.application.TimeseriesPoint;

final class StatsResponseMapper {

    private StatsResponseMapper() {
    }

    static SummaryResponse toResponse(SummaryStats stats) {
        return new SummaryResponse(
                stats.visitors(),
                stats.visits(),
                stats.pageviews(),
                stats.bounceRate(),
                stats.averageVisitDuration());
    }

    static TimeseriesPointResponse toResponse(TimeseriesPoint point) {
        return new TimeseriesPointResponse(point.bucket(), point.visitors(), point.pageviews());
    }

    static BreakdownEntryResponse toResponse(BreakdownEntry entry) {
        return new BreakdownEntryResponse(
                entry.key(), entry.label(), entry.visitors(), entry.visits(), entry.pageviews());
    }
}
