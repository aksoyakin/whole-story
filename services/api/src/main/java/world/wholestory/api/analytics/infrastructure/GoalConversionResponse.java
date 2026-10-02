package world.wholestory.api.analytics.infrastructure;

import java.util.UUID;

/**
 * @param type        {@code EVENT} or {@code PAGEVIEW}
 * @param target      what the goal looks for, which is also what the dashboard prints as its name
 * @param visitors    distinct visitors who completed it
 * @param completions how many times in total
 * @param rate        0..1, share of the period's visitors
 */
record GoalConversionResponse(UUID goalId, String type, String target,
                              long visitors, long completions, double rate) {
}
