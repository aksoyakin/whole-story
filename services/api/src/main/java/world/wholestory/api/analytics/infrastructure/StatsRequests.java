package world.wholestory.api.analytics.infrastructure;

import world.wholestory.api.analytics.application.Dimension;
import world.wholestory.api.analytics.application.Filter;
import world.wholestory.api.analytics.application.GoalDefinition;
import world.wholestory.api.site.ReadableGoal;

import java.util.List;

/**
 * Reading what a reporting request asks for. Shared by the dashboard's endpoints and the public ones, so that
 * a shared dashboard cannot end up with slightly different rules from the one its owner sees.
 */
final class StatsRequests {

    /** Enough for any list a dashboard shows, and a ceiling on what one request can ask the database for. */
    static final int MAX_BREAKDOWN_SIZE = 100;
    /** More than one filter per dimension is already unusual; this only stops an absurd request. */
    private static final int MAX_FILTERS = 10;

    private StatsRequests() {
    }

    static int breakdownSize(int limit) {
        return Math.clamp(limit, 1, MAX_BREAKDOWN_SIZE);
    }

    /**
     * Reads the repeated {@code filter=DIMENSION:value} parameter. The dimension is the same enum a breakdown
     * groups by, so a row the dashboard showed can be clicked straight into a filter, and the value is split
     * off at the first colon because a path contains colons of its own.
     * <p>
     * An empty value is meaningful: it is the row a breakdown returns for visits where the dimension is unknown.
     */
    static List<Filter> parseFilters(List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        if (filters.size() > MAX_FILTERS) {
            throw new IllegalArgumentException("too many filters");
        }
        return filters.stream().map(StatsRequests::parseOne).toList();
    }

    private static Filter parseOne(String filter) {
        int separator = filter.indexOf(':');
        if (separator < 1) {
            throw new IllegalArgumentException("a filter looks like DIMENSION:value");
        }
        Dimension dimension = Dimension.valueOf(filter.substring(0, separator));
        return new Filter(dimension, filter.substring(separator + 1));
    }

    /** Site Management's vocabulary into this side's. {@code PAGEVIEW} is a page here: there is nothing else. */
    static GoalDefinition toDefinition(ReadableGoal goal) {
        GoalDefinition.Kind kind = "PAGEVIEW".equals(goal.type())
                ? GoalDefinition.Kind.PAGE
                : GoalDefinition.Kind.EVENT;
        return new GoalDefinition(goal.goalId(), kind, goal.target());
    }
}
