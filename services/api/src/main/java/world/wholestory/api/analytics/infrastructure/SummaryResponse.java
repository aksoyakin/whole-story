package world.wholestory.api.analytics.infrastructure;

/** @param bounceRate 0..1, and {@code averageVisitDuration} is in seconds */
record SummaryResponse(long visitors, long visits, long pageviews, double bounceRate, double averageVisitDuration) {
}
