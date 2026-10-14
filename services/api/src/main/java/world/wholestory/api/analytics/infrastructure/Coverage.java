package world.wholestory.api.analytics.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * How a reporting range splits between the daily rollup and the sessions themselves.
 * <p>
 * The rollup counts whole UTC days, because that is the grain the hashing salt rotates on and the only one
 * where visitors add up (D-159). A site's day is cut in its own timezone (ADR 0020), so a range almost never
 * begins or ends on a UTC midnight: the two partial days at the edges have to be counted live. Measured,
 * taking the whole overlapping days instead was 1.7% wrong, and silently.
 * <p>
 * Anything the rollup has not reached yet falls into the tail and is counted live as well, which is what
 * makes the rollup an accelerator rather than an authority: if the job is behind, or has never run, the
 * answer is the same and only slower.
 *
 * @param rollupFrom first whole UTC day taken from the rollup
 * @param rollupTo   exclusive end of the rollup's part; equal to {@code rollupFrom} when it covers nothing
 */
record Coverage(LocalDate rollupFrom, LocalDate rollupTo,
                Instant headFrom, Instant headTo, Instant tailFrom, Instant tailTo) {

    static Coverage of(Instant start, Instant end, LocalDate coveredThrough) {
        LocalDate firstWhole = ceilToDay(start);
        LocalDate lastWholeExclusive = floorToDay(end);
        LocalDate watermarkEnd = coveredThrough == null ? firstWhole : coveredThrough.plusDays(1);

        LocalDate rollupTo = min(lastWholeExclusive, watermarkEnd);
        if (rollupTo.isBefore(firstWhole)) {
            rollupTo = firstWhole;
        }

        Instant headTo = earliest(midnight(firstWhole), end);
        Instant tailFrom = latest(midnight(rollupTo), start);
        return new Coverage(firstWhole, rollupTo,
                start, latest(headTo, start),
                earliest(tailFrom, end), end);
    }

    boolean usesRollup() {
        return rollupFrom.isBefore(rollupTo);
    }

    private static LocalDate ceilToDay(Instant at) {
        Instant truncated = at.truncatedTo(ChronoUnit.DAYS);
        return LocalDate.ofInstant(truncated.equals(at) ? at : truncated.plus(1, ChronoUnit.DAYS), ZoneOffset.UTC);
    }

    private static LocalDate floorToDay(Instant at) {
        return LocalDate.ofInstant(at.truncatedTo(ChronoUnit.DAYS), ZoneOffset.UTC);
    }

    private static Instant midnight(LocalDate day) {
        return day.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
