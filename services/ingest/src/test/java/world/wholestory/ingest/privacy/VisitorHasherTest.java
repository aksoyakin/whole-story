package world.wholestory.ingest.privacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorHasherTest {

    private static final byte[] TODAY_SALT = "salt-of-the-day".getBytes(StandardCharsets.UTF_8);
    private static final byte[] YESTERDAY_SALT = "salt-of-yesterday".getBytes(StandardCharsets.UTF_8);
    private static final LocalDate TODAY = LocalDate.parse("2026-09-28");
    private static final UUID SITE = UUID.fromString("0199a1b2-0000-7000-8000-000000000001");
    private static final String IP = "203.0.113.7";
    private static final String UA = "Mozilla/5.0";

    private final VisitorHasher hasher = new VisitorHasher(new StubSalts(Map.of(
            TODAY, TODAY_SALT,
            TODAY.minusDays(1), YESTERDAY_SALT)));

    @Test
    void isStableForTheSameVisitorOnTheSameDay() {
        assertThat(VisitorHasher.hash(TODAY_SALT, SITE, IP, UA)).isEqualTo(VisitorHasher.hash(TODAY_SALT, SITE, IP, UA));
    }

    @Test
    void changesWhenTheSaltRotates() {
        assertThat(VisitorHasher.hash(YESTERDAY_SALT, SITE, IP, UA))
                .isNotEqualTo(VisitorHasher.hash(TODAY_SALT, SITE, IP, UA));
    }

    @Test
    void isScopedToTheSite() {
        UUID otherSite = UUID.fromString("0199a1b2-0000-7000-8000-000000000002");

        assertThat(VisitorHasher.hash(TODAY_SALT, otherSite, IP, UA))
                .isNotEqualTo(VisitorHasher.hash(TODAY_SALT, SITE, IP, UA));
    }

    @Test
    void separatesFieldsSoConcatenationsCannotCollide() {
        assertThat(VisitorHasher.hash(TODAY_SALT, SITE, "1.2.3.4", "5 UA"))
                .isNotEqualTo(VisitorHasher.hash(TODAY_SALT, SITE, "1.2.3.45", " UA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-28T00:00:00Z", "2026-09-28T00:15:00Z", "2026-09-28T00:29:59Z"})
    void alsoHashesWithYesterdaysSaltJustAfterMidnight(String at) {
        VisitorIdentity identity = hasher.identify(Instant.parse(at), SITE, IP, UA);

        assertThat(identity.hash()).isEqualTo(VisitorHasher.hash(TODAY_SALT, SITE, IP, UA));
        assertThat(identity.previousHash()).isEqualTo(VisitorHasher.hash(YESTERDAY_SALT, SITE, IP, UA));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-28T00:30:00Z", "2026-09-28T09:00:00Z", "2026-09-28T23:59:59Z"})
    void stopsHashingWithYesterdaysSaltOnceTheGracePeriodIsOver(String at) {
        VisitorIdentity identity = hasher.identify(Instant.parse(at), SITE, IP, UA);

        assertThat(identity.hash()).isEqualTo(VisitorHasher.hash(TODAY_SALT, SITE, IP, UA));
        assertThat(identity.previousHash()).isNull();
    }

    @Test
    void hasNoPreviousHashWhenYesterdayHadNoTraffic() {
        VisitorHasher withoutYesterday = new VisitorHasher(new StubSalts(Map.of(TODAY, TODAY_SALT)));

        VisitorIdentity identity = withoutYesterday.identify(Instant.parse("2026-09-28T00:10:00Z"), SITE, IP, UA);

        assertThat(identity.previousHash()).isNull();
    }

    private record StubSalts(Map<LocalDate, byte[]> salts) implements DailySalts {

        @Override
        public byte[] saltFor(LocalDate utcDate) {
            return salts.get(utcDate);
        }

        @Override
        public Optional<byte[]> existingSaltFor(LocalDate utcDate) {
            return Optional.ofNullable(salts.get(utcDate));
        }
    }
}
