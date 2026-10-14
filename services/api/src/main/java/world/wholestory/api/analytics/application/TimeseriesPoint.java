package world.wholestory.api.analytics.application;

import java.time.Instant;

/**
 * One bucket of the time series. Empty buckets are present with zeroes, because a chart has to draw the gap
 * rather than skip it.
 *
 * @param bucket start of the bucket, as an instant; the label is the client's business
 * @param value  the one measure the request asked for ({@link Measure}); which it is, the caller knows
 */
public record TimeseriesPoint(Instant bucket, long value) {
}
