package world.wholestory.processor.enrichment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    private static final String CHROME_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    private static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1";
    private static final String SAFARI_IPAD = "Mozilla/5.0 (iPad; CPU OS 18_1 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/18.1 Safari/604.1";

    private final UserAgentParser parser = new UserAgentParser();

    @Test
    void describesADesktopBrowser() {
        ClientProfile client = parser.parse(CHROME_MAC).orElseThrow();

        assertThat(client.browser()).isEqualTo("Chrome");
        assertThat(client.browserVersion()).isEqualTo("141");
        assertThat(client.os()).isEqualTo("Mac OS");
        assertThat(client.deviceType()).isEqualTo("desktop");
    }

    @ParameterizedTest
    @CsvSource({
            "Chrome/Android, mobile",
            "Safari/iPhone,  mobile",
            "Safari/iPad,    tablet",
    })
    void mapsDeviceClassesOntoTheThreeStoredTypes(String label, String expected) {
        String userAgent = switch (label) {
            case "Chrome/Android" -> "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36";
            case "Safari/iPhone" -> SAFARI_IPHONE;
            default -> SAFARI_IPAD;
        };

        assertThat(parser.parse(userAgent).orElseThrow().deviceType()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            "curl/8.7.1",
            "python-requests/2.32.3",
    })
    void rejectsBotsAndCrawlers(String userAgent) {
        assertThat(parser.parse(userAgent)).isEmpty();
    }

    @Test
    void rejectsHeadlessBrowsersEvenThoughTheyLookLikeADesktop() {
        // Yauaa classifies this as a human Desktop; a scraper driving a real browser is still not a visit.
        String headless = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "HeadlessChrome/141.0.0.0 Safari/537.36";

        assertThat(parser.parse(headless)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-user-agent-at-all"})
    void rejectsMissingAndNonsensicalUserAgents(String userAgent) {
        assertThat(parser.parse(userAgent)).isEmpty();
    }

    @Test
    void neverStoresYauaasUnknownMarker() {
        // Linux desktops have no reportable OS version: Yauaa answers "??", which must not reach the database.
        String linuxChrome = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/141.0.0.0 Safari/537.36";

        ClientProfile client = parser.parse(linuxChrome).orElseThrow();

        assertThat(client.os()).isEqualTo("Linux");
        assertThat(client.osVersion()).isNull();
    }
}
