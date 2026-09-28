package world.wholestory.processor.enrichment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReferrerClassifierTest {

    private static final String SITE = "wholestory.world";

    private final ReferrerClassifier classifier = new ReferrerClassifier();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.google.com/",
            "https://google.de/",
            "https://www.google.co.uk/search?q=privacy+analytics",
    })
    void groupsTheCountryDomainsOfOneSourceUnderOneName(String referrer) {
        // The whole reason for using a referrer database: google.de and google.com are one source, not two rows.
        assertThat(classifier.classify(referrer, SITE)).isEqualTo("Google");
    }

    @Test
    void distinguishesSourcesThatDifferOnlyByPath() {
        assertThat(classifier.classify("https://www.bing.com/images/search?q=cat", SITE)).isEqualTo("Bing Images");
        assertThat(classifier.classify("https://www.bing.com/", SITE)).isEqualTo("Bing");
    }

    @ParameterizedTest
    @CsvSource({
            "https://t.co/abc123,                    Twitter",
            "https://lnkd.in/abc,                    LinkedIn",
            "https://l.facebook.com/l.php?u=x,       Facebook",
            "https://news.ycombinator.com/item?id=1, Hacker News",
            "https://chatgpt.com/,                   ChatGPT",
            "https://mail.google.com/,               Gmail",
    })
    void resolvesLinkWrappersAndSubdomainsToTheirSource(String referrer, String expected) {
        assertThat(classifier.classify(referrer, SITE)).isEqualTo(expected);
    }

    @Test
    void fallsBackToTheHostSoUnknownSourcesAreStillCounted() {
        assertThat(classifier.classify("https://some-unknown-blog.example/post/1", SITE))
                .isEqualTo("some-unknown-blog.example");
        assertThat(classifier.classify("https://www.some-unknown-blog.example/", SITE))
                .isEqualTo("some-unknown-blog.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://wholestory.world/pricing", "https://www.wholestory.world/"})
    void treatsNavigationInsideTheTrackedSiteAsDirect(String referrer) {
        assertThat(classifier.classify(referrer, SITE)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not a url", "gibberish"})
    void treatsMissingAndUnusableReferrersAsDirect(String referrer) {
        assertThat(classifier.classify(referrer, SITE)).isNull();
    }
}
