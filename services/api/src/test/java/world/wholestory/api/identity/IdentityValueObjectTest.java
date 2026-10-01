package world.wholestory.api.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.InvalidEmailAddressException;
import world.wholestory.api.identity.domain.InvalidNameException;
import world.wholestory.api.identity.domain.Password;
import world.wholestory.api.identity.domain.PasswordHash;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.WeakPasswordException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityValueObjectTest {

    @Test
    void anEmailAddressIsTrimmedAndLowerCased() {
        assertThat(EmailAddress.of("  Ada@Example.COM ").value()).isEqualTo("ada@example.com");
    }

    @Test
    void addressesThatDifferOnlyInCaseAreTheSame() {
        assertThat(EmailAddress.of("ADA@example.com")).isEqualTo(EmailAddress.of("ada@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ada", "@example.com", "ada@", "ada@example", "ada @example.com",
            "ada@@example.com", "ada@.com", "ada@example."})
    void structurallyImpossibleAddressesAreRejected(String candidate) {
        assertThatThrownBy(() -> EmailAddress.of(candidate)).isInstanceOf(InvalidEmailAddressException.class);
    }

    @Test
    void aPasswordNeverAppearsInItsOwnStringForm() {
        Password password = Password.of("correct horse battery staple");

        assertThat(password.toString()).doesNotContain("horse").isEqualTo("Password(hidden)");
        assertThat(PasswordHash.of("{bcrypt}$2a$12$abc").toString()).doesNotContain("bcrypt");
    }

    @Test
    void aPasswordHasToBeLongEnoughButNothingElseIsDemanded() {
        assertThatThrownBy(() -> Password.of("short")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> Password.of("x".repeat(Password.MAX_LENGTH + 1)))
                .isInstanceOf(WeakPasswordException.class);
        assertThat(Password.of("allofitlowercase").value()).isEqualTo("allofitlowercase");
    }

    @Test
    void theRejectionMessageDoesNotRepeatThePassword() {
        assertThatThrownBy(() -> Password.of("hunter2"))
                .hasMessageNotContaining("hunter2");
    }

    @Test
    void aNameIsTrimmedAndRequired() {
        assertThat(PersonName.of("  Ada Lovelace ").value()).isEqualTo("Ada Lovelace");
        assertThatThrownBy(() -> PersonName.of("   ")).isInstanceOf(InvalidNameException.class);
        assertThatThrownBy(() -> PersonName.of("x".repeat(121))).isInstanceOf(InvalidNameException.class);
    }
}
