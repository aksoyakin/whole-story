package world.wholestory.api.analytics.application;

import java.util.UUID;

/**
 * How many people are on a site right now.
 * <p>
 * A port of its own rather than another method on {@link StatsQueries}, because it answers from somewhere else
 * entirely: the processor keeps it in Redis as events arrive, so there is no date range, no filter and no SQL
 * here. The two only look alike from the dashboard.
 */
public interface RealtimeVisitors {

    /** Distinct visitors seen within the window; zero for a site nobody is reading. */
    long on(UUID siteId);
}
