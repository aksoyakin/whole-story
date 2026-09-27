package world.wholestory.api.analytics.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Inclusive date range. M1 interprets dates in UTC; the site's timezone is applied once sites exist (D-020). */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
    }

    public Instant start() {
        return from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
