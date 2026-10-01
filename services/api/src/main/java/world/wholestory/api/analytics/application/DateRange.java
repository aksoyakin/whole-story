package world.wholestory.api.analytics.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive range of days in the site's own timezone.
 * <p>
 * The dates a dashboard asks for are local dates: "yesterday" in Istanbul is not "yesterday" in Los Angeles.
 * The zone comes from the site rather than from whoever is looking, so two people see the same numbers (D-020).
 */
public record DateRange(LocalDate from, LocalDate to, ZoneId zone) {

    public DateRange {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
    }

    public static DateRange of(LocalDate from, LocalDate to, String timezone) {
        return new DateRange(from, to, ZoneId.of(timezone));
    }

    public Instant start() {
        return from.atStartOfDay(zone).toInstant();
    }

    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** A single day is read hour by hour; anything longer, day by day. */
    public Interval naturalInterval() {
        return days() == 1 ? Interval.HOUR : Interval.DAY;
    }
}
