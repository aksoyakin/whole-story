package world.wholestory.processor.rollup;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boundary a day is counted on. It matters because a day is counted once and the insert does nothing on
 * conflict, so reading a day too early does not produce a figure that is later corrected — it produces one
 * that is wrong for as long as the table exists.
 */
class VisitorDailyRollupTest {

    @Test
    void aDayIsNotCountedTheMomentItEnds() {
        assertThat(VisitorDailyRollup.lastDayToCompute(Instant.parse("2026-10-14T00:00:00Z")))
                .isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(VisitorDailyRollup.lastDayToCompute(Instant.parse("2026-10-14T00:59:59Z")))
                .isEqualTo(LocalDate.parse("2026-10-12"));
    }

    @Test
    void itIsCountedOnceTheLateArrivalsHaveHadTimeToLand() {
        assertThat(VisitorDailyRollup.lastDayToCompute(Instant.parse("2026-10-14T01:00:00Z")))
                .isEqualTo(LocalDate.parse("2026-10-13"));
        assertThat(VisitorDailyRollup.lastDayToCompute(Instant.parse("2026-10-14T23:59:59Z")))
                .isEqualTo(LocalDate.parse("2026-10-13"));
    }
}
