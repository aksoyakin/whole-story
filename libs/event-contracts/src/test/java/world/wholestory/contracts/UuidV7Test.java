package world.wholestory.contracts;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void isVersion7WithRfcVariant() {
        UUID uuid = UuidV7.generate();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheTimestampInTheFirst48Bits() {
        Instant time = Instant.parse("2026-09-26T12:34:56.789Z");

        UUID uuid = UuidV7.generate(time);

        assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(time.toEpochMilli());
    }

    @Test
    void sortsByCreationTime() {
        UUID earlier = UuidV7.generate(Instant.parse("2026-09-26T00:00:00Z"));
        UUID later = UuidV7.generate(Instant.parse("2026-09-26T00:00:01Z"));

        assertThat(earlier.toString()).isLessThan(later.toString());
    }
}
