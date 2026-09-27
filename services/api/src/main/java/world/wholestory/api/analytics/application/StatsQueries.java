package world.wholestory.api.analytics.application;

import java.util.UUID;

/** Read-side port over processor's analytics views. */
public interface StatsQueries {

    AggregateStats aggregate(UUID siteId, DateRange range);
}
