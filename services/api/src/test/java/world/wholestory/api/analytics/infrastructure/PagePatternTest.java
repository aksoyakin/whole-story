package world.wholestory.api.analytics.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PagePatternTest {

    @ParameterizedTest
    @CsvSource({
            "/thanks,            /thanks",
            "/blog/*,            /blog/%",
            "/*/success,         /%/success",
            "/a*b*c,             /a%b%c",
    })
    void theProductsWildcardBecomesSqls(String pattern, String expected) {
        assertThat(PagePattern.toLikePattern(pattern)).isEqualTo(expected);
    }

    /**
     * The point of the escaping. Without it this path would match everything beginning with /100, because
     * the per cent sign is SQL's own wildcard — a goal would silently count far more than it says.
     */
    @Test
    void sqlsOwnWildcardsInAPathAreEscaped() {
        assertThat(PagePattern.toLikePattern("/100%-free")).isEqualTo("/100\\%-free");
        assertThat(PagePattern.toLikePattern("/a_b")).isEqualTo("/a\\_b");
        assertThat(PagePattern.toLikePattern("/back\\slash")).isEqualTo("/back\\\\slash");
    }

    @Test
    void anExactPathStaysExact() {
        // No wildcard anywhere, so nothing in the result can match more than the path itself.
        String like = PagePattern.toLikePattern("/pricing");

        assertThat(like).doesNotContain("%").doesNotContain("_");
    }
}
