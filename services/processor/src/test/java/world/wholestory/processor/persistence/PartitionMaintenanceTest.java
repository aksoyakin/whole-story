package world.wholestory.processor.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The retention boundary, at a fixed instant rather than at whenever the suite runs. */
class PartitionMaintenanceTest {

    @Test
    void theHorizonIsTheFirstDayOfTheMonthTwentyFourMonthsBack() {
        assertThat(PartitionMaintenance.cutoff(Instant.parse("2028-10-16T09:30:00Z")))
                .isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    /**
     * Which makes the kept window between 24 and 25 months rather than exactly 24: in the middle of October
     * 2028 the oldest month still covered is October 2026, all of it. A month is dropped when the whole of it
     * is older than the horizon, so the day of the month never shortens what is kept — it only decides which
     * month the horizon lands in.
     */
    @Test
    void theDayOfTheMonthDoesNotMoveTheHorizon() {
        assertThat(PartitionMaintenance.cutoff(Instant.parse("2028-10-01T00:00:00Z")))
                .isEqualTo(PartitionMaintenance.cutoff(Instant.parse("2028-10-31T23:59:59Z")));
    }
}
