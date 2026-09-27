package world.wholestory.api.analytics;

import org.junit.jupiter.api.Test;
import world.wholestory.api.analytics.application.DateRange;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DateRangeTest {

    @Test
    void coversWholeDaysInclusively() {
        DateRange range = new DateRange(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-26"));

        assertThat(range.start()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(range.endExclusive()).isEqualTo(Instant.parse("2026-09-27T00:00:00Z"));
    }

    @Test
    void rejectsReversedRanges() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DateRange(LocalDate.parse("2026-09-26"), LocalDate.parse("2026-09-01")));
    }
}
