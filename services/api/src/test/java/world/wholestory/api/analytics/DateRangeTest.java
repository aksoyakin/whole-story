package world.wholestory.api.analytics;

import org.junit.jupiter.api.Test;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.Interval;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    void aDayStartsAndEndsWhereTheSiteSaysItDoes() {
        DateRange istanbul = DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), "Europe/Istanbul");

        assertThat(istanbul.start()).isEqualTo(Instant.parse("2026-09-30T21:00:00Z"));
        assertThat(istanbul.endExclusive()).isEqualTo(Instant.parse("2026-10-01T21:00:00Z"));
    }

    @Test
    void theSameDatesMeanDifferentMomentsInDifferentZones() {
        LocalDate day = LocalDate.of(2026, 10, 1);

        Instant istanbul = DateRange.of(day, day, "Europe/Istanbul").start();
        Instant losAngeles = DateRange.of(day, day, "America/Los_Angeles").start();

        assertThat(istanbul).isBefore(losAngeles);
        assertThat(Duration.between(istanbul, losAngeles)).isEqualTo(Duration.ofHours(10));
    }

    /** A day is not always 24 hours long, and the range has to follow the zone rather than assume. */
    @Test
    void aDayAcrossADaylightSavingChangeIsShorterOrLonger() {
        LocalDate springForward = LocalDate.of(2026, 3, 8);
        LocalDate fallBack = LocalDate.of(2026, 11, 1);

        DateRange shortDay = DateRange.of(springForward, springForward, "America/New_York");
        DateRange longDay = DateRange.of(fallBack, fallBack, "America/New_York");

        assertThat(Duration.between(shortDay.start(), shortDay.endExclusive())).isEqualTo(Duration.ofHours(23));
        assertThat(Duration.between(longDay.start(), longDay.endExclusive())).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void theRangeIsInclusiveOnBothEnds() {
        DateRange week = DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), "UTC");

        assertThat(week.days()).isEqualTo(7);
        assertThat(week.endExclusive()).isEqualTo(Instant.parse("2026-10-08T00:00:00Z"));
    }

    @Test
    void oneDayIsReadHourByHourAndAnythingLongerDayByDay() {
        LocalDate day = LocalDate.of(2026, 10, 1);

        assertThat(DateRange.of(day, day, "UTC").naturalInterval()).isEqualTo(Interval.HOUR);
        assertThat(DateRange.of(day, day.plusDays(1), "UTC").naturalInterval()).isEqualTo(Interval.DAY);
    }

    @Test
    void aBackwardsRangeIsRejected() {
        LocalDate day = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> DateRange.of(day.plusDays(1), day, "UTC"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
