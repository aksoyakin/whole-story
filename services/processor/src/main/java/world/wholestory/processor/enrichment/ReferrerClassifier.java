package world.wholestory.processor.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a referrer URL into the name of the traffic source ("Google", "Hacker News").
 * <p>
 * The mapping is the referer-parser database (see the README next to the data file). Only the domains are used:
 * the search-keyword parameters in that file are deliberately ignored, because what someone searched for is not
 * something this product collects.
 */
@Slf4j
@Component
public class ReferrerClassifier {

    private static final String DATABASE = "referrer/referers.yml";
    private static final String WWW = "www.";

    /** Keys are {@code host} or {@code host/path}, as they appear in the database. */
    private final Map<String, String> sourcesByDomain = load();

    /**
     * @param referrer the referring URL, may be null
     * @param hostname the hostname of the page that was viewed, used to recognise internal navigation
     * @return the source name, or null for direct traffic and for navigation inside the tracked site
     */
    public String classify(String referrer, String hostname) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(referrer.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (isSameSite(host, hostname)) {
            return null;
        }
        String known = lookup(host, uri.getPath());
        return known != null ? known : withoutWww(host);
    }

    /** A visitor moving between pages of the tracked site is not arriving from a source. */
    private static boolean isSameSite(String referrerHost, String hostname) {
        return hostname != null && withoutWww(referrerHost).equals(withoutWww(hostname.toLowerCase(Locale.ROOT)));
    }

    /**
     * The database distinguishes sources by path as well ({@code bing.com/images/search}), so the longest path
     * prefix wins before falling back to the bare host.
     */
    private String lookup(String host, String path) {
        for (String candidateHost : List.of(host, withoutWww(host))) {
            String candidatePath = normalisePath(path);
            while (!candidatePath.isEmpty()) {
                String source = sourcesByDomain.get(candidateHost + candidatePath);
                if (source != null) {
                    return source;
                }
                candidatePath = candidatePath.substring(0, candidatePath.lastIndexOf('/'));
            }
            String source = sourcesByDomain.get(candidateHost);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private static String normalisePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        String normalised = path.toLowerCase(Locale.ROOT);
        return normalised.endsWith("/") ? normalised.substring(0, normalised.length() - 1) : normalised;
    }

    private static String withoutWww(String host) {
        return host.startsWith(WWW) ? host.substring(WWW.length()) : host;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> load() {
        Map<String, String> sources = new HashMap<>();
        try (InputStream stream = new ClassPathResource(DATABASE).getInputStream()) {
            Map<String, Map<String, Map<String, List<String>>>> byMedium = new Yaml().load(stream);
            byMedium.forEach((medium, providers) -> providers.forEach((name, definition) -> {
                List<String> domains = definition.get("domains");
                if (domains != null) {
                    // A provider can appear under several mediums; the first name for a domain wins.
                    domains.forEach(domain -> sources.putIfAbsent(normaliseKey(domain), name));
                }
            }));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the referrer database " + DATABASE, e);
        }
        log.info("Loaded {} referrer domains", sources.size());
        return sources;
    }

    private static String normaliseKey(String domain) {
        String key = domain.trim().toLowerCase(Locale.ROOT);
        return key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
    }
}
