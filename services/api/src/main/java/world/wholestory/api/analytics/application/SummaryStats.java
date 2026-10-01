package world.wholestory.api.analytics.application;

/**
 * The headline numbers.
 *
 * @param visitors               distinct visitors, counted exactly rather than summed: a visitor seen in two
 *                               hours is one visitor, which is why no rollup can hold this (D-031)
 * @param pageviews              from the hourly rollup, which is additive and therefore may be summed
 * @param bounceRate             share of sessions where nothing meaningful happened (ADR 0017), 0..1
 * @param averageVisitDuration   seconds between a session's first and last event
 */
public record SummaryStats(
        long visitors,
        long visits,
        long pageviews,
        double bounceRate,
        double averageVisitDuration) {

    public static final SummaryStats NONE = new SummaryStats(0, 0, 0, 0, 0);
}
