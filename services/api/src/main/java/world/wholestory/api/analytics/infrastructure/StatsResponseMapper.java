package world.wholestory.api.analytics.infrastructure;

import world.wholestory.api.analytics.application.BreakdownEntry;
import world.wholestory.api.analytics.application.GoalConversion;
import world.wholestory.api.analytics.application.SummaryStats;
import world.wholestory.api.analytics.application.TimeseriesPoint;
import world.wholestory.api.site.ReadableGoal;

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

    /**
     * The goal's own description comes from Site Management and the numbers from this side, so the two are
     * paired here rather than either one carrying the other's data.
     */
    static GoalConversionResponse toResponse(GoalConversion conversion, ReadableGoal goal) {
        return new GoalConversionResponse(
                conversion.goalId(),
                goal.type(),
                goal.target(),
                conversion.visitors(),
                conversion.completions(),
                conversion.rate());
    }

    static BreakdownEntryResponse toResponse(BreakdownEntry entry) {
        return new BreakdownEntryResponse(
                entry.key(), entry.label(), entry.visitors(), entry.visits(), entry.pageviews());
    }
}
