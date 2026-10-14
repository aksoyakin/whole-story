package world.wholestory.api.analytics.infrastructure;

import java.time.Instant;

/**
 * @param bucket start of the bucket; the client renders it in the site's timezone
 * @param value  the measure asked for, named by the request rather than by this field — the same reason a
 *               breakdown row carries one number (D-168)
 */
record TimeseriesPointResponse(Instant bucket, long value) {
}
