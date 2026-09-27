package world.wholestory.ingest.privacy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorHasherTest {

    private static final byte[] SALT = "salt-of-the-day".getBytes(StandardCharsets.UTF_8);
    private static final UUID SITE = UUID.fromString("0199a1b2-0000-7000-8000-000000000001");
    private static final String IP = "203.0.113.7";
    private static final String UA = "Mozilla/5.0";

    @Test
    void isStableForTheSameVisitorOnTheSameDay() {
        assertThat(VisitorHasher.hash(SALT, SITE, IP, UA)).isEqualTo(VisitorHasher.hash(SALT, SITE, IP, UA));
    }

    @Test
    void changesWhenTheSaltRotates() {
        byte[] tomorrow = "salt-of-tomorrow".getBytes(StandardCharsets.UTF_8);

        assertThat(VisitorHasher.hash(tomorrow, SITE, IP, UA)).isNotEqualTo(VisitorHasher.hash(SALT, SITE, IP, UA));
    }

    @Test
    void isScopedToTheSite() {
        UUID otherSite = UUID.fromString("0199a1b2-0000-7000-8000-000000000002");

        assertThat(VisitorHasher.hash(SALT, otherSite, IP, UA)).isNotEqualTo(VisitorHasher.hash(SALT, SITE, IP, UA));
    }

    @Test
    void separatesFieldsSoConcatenationsCannotCollide() {
        assertThat(VisitorHasher.hash(SALT, SITE, "1.2.3.4", "5 UA")).isNotEqualTo(VisitorHasher.hash(SALT, SITE, "1.2.3.45", " UA"));
    }
}
