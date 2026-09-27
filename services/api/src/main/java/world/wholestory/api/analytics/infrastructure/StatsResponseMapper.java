package world.wholestory.api.analytics.infrastructure;

import world.wholestory.api.analytics.application.AggregateStats;

final class StatsResponseMapper {

    private StatsResponseMapper() {
    }

    static AggregateStatsResponse toResponse(AggregateStats stats) {
        return new AggregateStatsResponse(stats.visitors(), stats.visits(), stats.pageviews());
    }
}
