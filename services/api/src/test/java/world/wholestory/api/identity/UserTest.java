package world.wholestory.api.identity;

import org.junit.jupiter.api.Test;
import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.PasswordHash;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.User;
import world.wholestory.api.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(3600);

    @Test
    void aNewAccountStartsUnverified() {
        User user = register();

        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.emailVerifiedAt()).isEmpty();
        assertThat(user.getCreatedAt()).isEqualTo(REGISTERED_AT);
        assertThat(user.getUpdatedAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void changingThePasswordRecordsWhenItHappened() {
        User user = register();

        user.changePassword(PasswordHash.of("{bcrypt}$2a$12$new"), LATER);

        assertThat(user.getPasswordHash().value()).isEqualTo("{bcrypt}$2a$12$new");
        assertThat(user.getUpdatedAt()).isEqualTo(LATER);
        assertThat(user.getCreatedAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void confirmingAnAlreadyConfirmedAddressKeepsTheFirstTime() {
        User user = register();
        user.confirmEmail(REGISTERED_AT);

        user.confirmEmail(LATER);

        assertThat(user.emailVerifiedAt()).contains(REGISTERED_AT);
    }

    private static User register() {
        return User.register(
                UserId.of(UUID.randomUUID()),
                EmailAddress.of("ada@example.com"),
                PersonName.of("Ada"),
                PasswordHash.of("{bcrypt}$2a$12$original"),
                REGISTERED_AT);
    }
}
