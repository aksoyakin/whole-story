package world.wholestory.api.analytics.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arithmetic the hybrid read rests on. Every case below is a way of getting a number silently wrong
 * rather than loudly: the parts have to tile the range exactly, with no gap and no overlap, because a gap
 * loses visitors and an overlap counts them twice — and either way the dashboard just shows a figure.
 */
class CoverageTest {

    /** Istanbul is UTC+3, so a site's day begins at 21:00 the previous day. That is the normal case here. */
    private static final Instant START = Instant.parse("2026-09-13T21:00:00Z");
    private static final Instant END = Instant.parse("2026-10-13T21:00:00Z");

    @Test
    void takesTheWholeDaysAndLeavesTheTwoEdgesLive() {
        Coverage coverage = Coverage.of(START, END, LocalDate.parse("2026-10-20"));

        assertThat(coverage.usesRollup()).isTrue();
        assertThat(coverage.rollupFrom()).isEqualTo(LocalDate.parse("2026-09-14"));
        assertThat(coverage.rollupTo()).isEqualTo(LocalDate.parse("2026-10-13"));
        // The three hours before the first whole day, and the twenty-one after the last.
        assertThat(coverage.headFrom()).isEqualTo(START);
        assertThat(coverage.headTo()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(coverage.tailFrom()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
        assertThat(coverage.tailTo()).isEqualTo(END);
    }

    @Test
    void theThreePartsCoverTheRangeExactlyAndDoNotOverlap() {
        Coverage coverage = Coverage.of(START, END, LocalDate.parse("2026-10-20"));

        assertThat(coverage.headFrom()).isEqualTo(START);
        assertThat(coverage.headTo()).isEqualTo(coverage.rollupFrom().atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        assertThat(coverage.tailFrom()).isEqualTo(coverage.rollupTo().atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        assertThat(coverage.tailTo()).isEqualTo(END);
    }

    /** A range already aligned to UTC midnight has no edges to count live. */
    @Test
    void anAlignedRangeIsAllRollup() {
        Coverage coverage = Coverage.of(
                Instant.parse("2026-09-14T00:00:00Z"), Instant.parse("2026-10-14T00:00:00Z"),
                LocalDate.parse("2026-10-20"));

        assertThat(coverage.rollupFrom()).isEqualTo(LocalDate.parse("2026-09-14"));
        assertThat(coverage.rollupTo()).isEqualTo(LocalDate.parse("2026-10-14"));
        assertThat(coverage.headFrom()).isEqualTo(coverage.headTo());
        assertThat(coverage.tailFrom()).isEqualTo(coverage.tailTo());
    }

    /** The job is behind: the days it has not reached move into the tail and get counted live. */
    @Test
    void whatTheRollupHasNotReachedIsCountedLive() {
        Coverage coverage = Coverage.of(START, END, LocalDate.parse("2026-09-30"));

        assertThat(coverage.rollupTo()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(coverage.tailFrom()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(coverage.tailTo()).isEqualTo(END);
    }

    /** Never run, or a fresh database: the answer must be the one the service always gave. */
    @Test
    void noWatermarkMeansEverythingIsCountedLive() {
        Coverage coverage = Coverage.of(START, END, null);

        assertThat(coverage.usesRollup()).isFalse();
        assertThat(coverage.headFrom()).isEqualTo(START);
        assertThat(coverage.headTo()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(coverage.tailFrom()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(coverage.tailTo()).isEqualTo(END);
    }

    /** A watermark from before the range is the same situation, and must not produce a backwards interval. */
    @Test
    void aWatermarkBeforeTheRangeCoversNothing() {
        Coverage coverage = Coverage.of(START, END, LocalDate.parse("2026-01-01"));

        assertThat(coverage.usesRollup()).isFalse();
        assertThat(coverage.rollupFrom()).isEqualTo(coverage.rollupTo());
        assertThat(coverage.tailFrom()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(coverage.tailTo()).isEqualTo(END);
    }

    /** One day in a shifted zone holds no whole UTC day at all. */
    @Test
    void aSingleLocalDayIsAllLive() {
        Coverage coverage = Coverage.of(
                Instant.parse("2026-10-12T21:00:00Z"), Instant.parse("2026-10-13T21:00:00Z"),
                LocalDate.parse("2026-10-20"));

        assertThat(coverage.usesRollup()).isFalse();
        assertThat(coverage.headFrom()).isEqualTo(Instant.parse("2026-10-12T21:00:00Z"));
        assertThat(coverage.headTo()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
        assertThat(coverage.tailFrom()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
        assertThat(coverage.tailTo()).isEqualTo(Instant.parse("2026-10-13T21:00:00Z"));
    }

    /** Today, which the rollup never covers: its whole range stays live. */
    @Test
    void todayIsNeverTakenFromTheRollup() {
        Coverage coverage = Coverage.of(
                Instant.parse("2026-10-13T21:00:00Z"), Instant.parse("2026-10-14T21:00:00Z"),
                LocalDate.parse("2026-10-13"));

        assertThat(coverage.usesRollup()).isFalse();
        assertThat(coverage.tailTo()).isEqualTo(Instant.parse("2026-10-14T21:00:00Z"));
    }
}
