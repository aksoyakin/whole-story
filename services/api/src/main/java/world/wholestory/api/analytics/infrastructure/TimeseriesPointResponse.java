package world.wholestory.api.analytics.infrastructure;

import java.time.Instant;

/** @param bucket start of the bucket; the client renders it in the site's timezone */
record TimeseriesPointResponse(Instant bucket, long visitors, long pageviews) {
}
