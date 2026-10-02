package world.wholestory.api.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import world.wholestory.api.identity.domain.InvalidResetTokenException;
import world.wholestory.api.identity.domain.ResetToken;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResetTokenTest {

    @Test
    void aGeneratedTokenCarries256BitsAndSurvivesAQueryString() {
        String value = ResetToken.generate().value();

        // 32 bytes, base64url without padding.
        assertThat(value).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void twoTokensAreNeverTheSame() {
        Set<String> seen = new HashSet<>();

        IntStream.range(0, 1_000).forEach(i -> seen.add(ResetToken.generate().value()));

        assertThat(seen).hasSize(1_000);
    }

    @Test
    void theHashIsStableAndTellsTokensApart() {
        ResetToken token = ResetToken.generate();

        assertThat(token.hash()).isEqualTo(ResetToken.of(token.value()).hash());
        assertThat(token.hash()).isNotEqualTo(ResetToken.generate().hash());
    }

    /** What reaches the database must not be the thing that opens the link. */
    @Test
    void theHashDoesNotContainTheToken() {
        ResetToken token = ResetToken.generate();

        assertThat(token.hash().value()).doesNotContain(token.value()).hasSize(64);
    }

    @Test
    void theTokenNeverAppearsInItsOwnStringForm() {
        ResetToken token = ResetToken.generate();

        assertThat(token.toString()).isEqualTo("ResetToken(hidden)").doesNotContain(token.value());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void somethingThatIsNotATokenIsRefusedTheSameWayAnUnknownOneIs(String value) {
        assertThatThrownBy(() -> ResetToken.of(value)).isInstanceOf(InvalidResetTokenException.class);
    }
}
