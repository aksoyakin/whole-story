package world.wholestory.api.analytics.application;

import java.util.UUID;

/**
 * How a goal did over a period.
 *
 * @param visitors    distinct visitors who completed it — the numerator of the rate, counted and not summed
 * @param completions how many times it happened in total, which can exceed the visitors who did it
 * @param rate        visitors who converted over all visitors in the period, 0..1; 0 when nobody came at all
 */
public record GoalConversion(UUID goalId, long visitors, long completions, double rate) {
}
