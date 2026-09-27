package world.wholestory.processor.sessionization;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStateTest {

    @Test
    void encodingIsLosslessDownToTheNanosecond() {
        SessionState state = new SessionState(UUID.randomUUID(),
                Instant.parse("2026-09-26T19:17:53.263073Z"), Instant.parse("2026-09-26T19:18:01.000000123Z"));

        assertThat(SessionState.decode(state.encode())).isEqualTo(state);
    }
}
