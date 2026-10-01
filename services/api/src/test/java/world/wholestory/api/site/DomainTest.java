package world.wholestory.api.site;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import world.wholestory.api.site.domain.Domain;
import world.wholestory.api.site.domain.InvalidDomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainTest {

    @Test
    void oneSiteCannotBeRegisteredTwiceUnderDifferentSpellings() {
        Domain canonical = Domain.of("example.com");

        assertThat(Domain.of("  EXAMPLE.com ")).isEqualTo(canonical);
        assertThat(Domain.of("www.example.com")).isEqualTo(canonical);
        assertThat(Domain.of("WWW.Example.COM")).isEqualTo(canonical);
    }

    @Test
    void subdomainsAreTheirOwnSites() {
        assertThat(Domain.of("blog.example.com")).isNotEqualTo(Domain.of("example.com"));
        assertThat(Domain.of("blog.example.com").value()).isEqualTo("blog.example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "example", "example..com", ".example.com", "example.com.",
            "https://example.com", "example.com/path", "example.com:443", "exa mple.com",
            "-example.com", "example-.com", "exa_mple.com", "192.168.1.1"})
    void anythingThatIsNotAPlainHostnameIsRejected(String candidate) {
        assertThatThrownBy(() -> Domain.of(candidate)).isInstanceOf(InvalidDomainException.class);
    }

    @Test
    void theRejectionNamesWhatWasGiven() {
        assertThatThrownBy(() -> Domain.of("example.com/admin")).hasMessageContaining("example.com/admin");
    }
}
