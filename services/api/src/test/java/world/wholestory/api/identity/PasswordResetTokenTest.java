package world.wholestory.api.identity;

import org.junit.jupiter.api.Test;
import world.wholestory.api.identity.domain.InvalidResetTokenException;
import world.wholestory.api.identity.domain.PasswordResetToken;
import world.wholestory.api.identity.domain.ResetToken;
import world.wholestory.api.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    void aFreshLinkCanBeFollowedOnce() {
        PasswordResetToken link = issue();

        link.redeem(ISSUED_AT.plusSeconds(60));

        assertThat(link.usedAt()).contains(ISSUED_AT.plusSeconds(60));
        assertThat(link.isUsable(ISSUED_AT.plusSeconds(61))).isFalse();
    }

    /** A reset link lives in an inbox, where it can be opened again long after it was used. */
    @Test
    void followingItTwiceIsRefused() {
        PasswordResetToken link = issue();
        link.redeem(ISSUED_AT.plusSeconds(60));

        assertThatThrownBy(() -> link.redeem(ISSUED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void itStopsWorkingAfterAnHour() {
        PasswordResetToken link = issue();
        Instant justBefore = ISSUED_AT.plus(PasswordResetToken.LIFETIME).minusSeconds(1);

        assertThat(link.isUsable(justBefore)).isTrue();
        assertThat(link.isUsable(ISSUED_AT.plus(PasswordResetToken.LIFETIME))).isFalse();
        assertThatThrownBy(() -> link.redeem(ISSUED_AT.plus(PasswordResetToken.LIFETIME)))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void retiringALinkKeepsWhenItWasFirstRetired() {
        PasswordResetToken link = issue();
        link.invalidate(ISSUED_AT.plusSeconds(10));

        link.invalidate(ISSUED_AT.plusSeconds(20));

        assertThat(link.usedAt()).contains(ISSUED_AT.plusSeconds(10));
        assertThat(link.isUsable(ISSUED_AT.plusSeconds(30))).isFalse();
    }

    /** Retiring is what happens to the other outstanding links when one of them is used, so it cannot refuse. */
    @Test
    void retiringAnExpiredLinkIsHarmless() {
        PasswordResetToken link = issue();

        link.invalidate(ISSUED_AT.plus(PasswordResetToken.LIFETIME).plusSeconds(1));

        assertThat(link.usedAt()).isPresent();
    }

    @Test
    void theSecretIsNotPartOfTheStoredLink() {
        ResetToken secret = ResetToken.generate();

        PasswordResetToken link = PasswordResetToken.issue(secret.hash(), someone(), ISSUED_AT);

        assertThat(link.getTokenHash().value()).isNotEqualTo(secret.value());
        assertThat(link.getTokenHash().value()).isEqualTo(secret.hash().value());
    }

    private static PasswordResetToken issue() {
        return PasswordResetToken.issue(ResetToken.generate().hash(), someone(), ISSUED_AT);
    }

    private static UserId someone() {
        return UserId.of(UUID.randomUUID());
    }
}
