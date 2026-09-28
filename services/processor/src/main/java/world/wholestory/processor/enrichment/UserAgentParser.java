package world.wholestory.processor.enrichment;

import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import nl.basjes.parse.useragent.classify.UserAgentClassifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Classifies a User-Agent with Yauaa and reduces it to the five values that are stored.
 * The raw User-Agent itself is never persisted.
 * <p>
 * Only the required fields are requested from the analyzer: that keeps the rule set, and with it the memory
 * footprint, down to roughly 34 MB instead of the ~114 MB it takes to extract everything.
 */
@Component
public class UserAgentParser {

    /** Yauaa's marker for "this field could not be determined". */
    private static final String UNKNOWN_VALUE = "??";

    /** Sized for the unique User-Agents seen within a session window, as Yauaa recommends. */
    private static final int CACHE_SIZE = 10_000;

    private final UserAgentAnalyzer analyzer = UserAgentAnalyzer.newBuilder()
            .withFields(UserAgent.DEVICE_CLASS, UserAgent.AGENT_NAME, UserAgent.AGENT_VERSION,
                    UserAgent.OPERATING_SYSTEM_NAME, UserAgent.OPERATING_SYSTEM_VERSION)
            .withCache(CACHE_SIZE)
            .hideMatcherLoadStats()
            .build();

    /**
     * @return the visitor's client, or empty when the User-Agent belongs to a bot, crawler or automation tool:
     *         those events are not visits and are dropped before they can reach sessions or rollups
     */
    public Optional<ClientProfile> parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return Optional.empty();
        }
        UserAgent agent = analyzer.parse(userAgent);
        if (!UserAgentClassifier.isHuman(agent) || isAutomatedBrowser(userAgent)) {
            return Optional.empty();
        }
        return Optional.of(new ClientProfile(
                value(agent, UserAgent.AGENT_NAME),
                value(agent, UserAgent.AGENT_VERSION),
                value(agent, UserAgent.OPERATING_SYSTEM_NAME),
                value(agent, UserAgent.OPERATING_SYSTEM_VERSION),
                deviceType(value(agent, UserAgent.DEVICE_CLASS))));
    }

    /**
     * Headless browsers report themselves as a normal desktop browser, so Yauaa classifies them as human.
     * Scrapers increasingly drive real browsers, and those page loads are not visits.
     */
    private static boolean isAutomatedBrowser(String userAgent) {
        return userAgent.contains("Headless");
    }

    /** The database stores the three device types a dashboard distinguishes; anything else stays unknown. */
    private static String deviceType(String deviceClass) {
        if (deviceClass == null) {
            return null;
        }
        return switch (deviceClass) {
            case "Desktop" -> "desktop";
            case "Phone", "Mobile" -> "mobile";
            case "Tablet" -> "tablet";
            default -> null;
        };
    }

    private static String value(UserAgent agent, String field) {
        String value = agent.getValue(field);
        return value == null || value.isBlank() || UNKNOWN_VALUE.equals(value) ? null : value;
    }
}
