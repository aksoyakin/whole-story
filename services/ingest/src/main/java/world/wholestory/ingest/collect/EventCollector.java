package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.UuidV7;
import world.wholestory.ingest.geo.GeoLocation;
import world.wholestory.ingest.geo.GeoResolver;
import world.wholestory.ingest.privacy.VisitorHasher;
import world.wholestory.ingest.privacy.VisitorIdentity;
import world.wholestory.ingest.publish.RawEventPublisher;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class EventCollector {

    private static final int MAX_NAME_LENGTH = 120;
    /** What a browser sends for an opaque origin, e.g. from a sandboxed iframe. */
    private static final String OPAQUE_ORIGIN = "null";

    private final SiteRegistry siteRegistry;
    private final VisitorHasher visitorHasher;
    private final RateLimiter rateLimiter;
    private final GeoResolver geoResolver;
    private final RawEventPublisher publisher;
    private final Clock clock;

    /**
     * Turns a tracker payload into a privacy-safe event. The IP address is only used for hashing and the GeoIP
     * lookup, and is dropped when this method returns.
     */
    void collect(IncomingEvent incoming, String ipAddress, String userAgent, String origin) {
        if (incoming.name() == null || incoming.name().isBlank() || incoming.name().length() > MAX_NAME_LENGTH) {
            throw new InvalidEventException("invalid_name", "invalid event name");
        }
        if (incoming.domain() == null || incoming.url() == null) {
            throw new InvalidEventException("incomplete", "domain and url are required");
        }
        String domain = Domains.normalize(incoming.domain());
        UUID siteId = siteRegistry.findSiteId(domain)
                .orElseThrow(() -> new InvalidEventException("unknown_domain", "unknown domain"));
        requireMatchingOrigin(origin, domain);
        UriComponents url = parse(incoming.url());

        Instant now = clock.instant();
        String agent = userAgent == null ? "" : userAgent;
        VisitorIdentity visitor = visitorHasher.identify(now, siteId, ipAddress, agent);
        if (!rateLimiter.allows(siteId, visitor.hash())) {
            throw new RateLimitExceededException();
        }
        GeoLocation location = geoResolver.resolve(ipAddress);

        publisher.publish(new RawEventV1(
                RawEventV1.SCHEMA_VERSION,
                UuidV7.generate(now),
                now,
                siteId,
                visitor.hash(),
                visitor.previousHash(),
                incoming.name(),
                url.getHost(),
                url.getPath() == null || url.getPath().isEmpty() ? "/" : url.getPath(),
                blankToNull(incoming.referrer()),
                url.getQueryParams().getFirst("utm_source"),
                url.getQueryParams().getFirst("utm_medium"),
                url.getQueryParams().getFirst("utm_campaign"),
                url.getQueryParams().getFirst("utm_content"),
                url.getQueryParams().getFirst("utm_term"),
                location.countryCode(),
                location.subdivisionCode(),
                location.cityGeonameId(),
                agent,
                incoming.props()));
    }

    /**
     * When the browser tells us which page sent the request, it has to be the tracked site. This stops a page on
     * another domain from reporting events for someone else's site through a real browser. A missing or opaque
     * origin is accepted: browsers are inconsistent about sending it on same-origin requests, and a client that
     * is not a browser can put anything there anyway.
     */
    private static void requireMatchingOrigin(String origin, String normalizedDomain) {
        if (origin == null || origin.isBlank() || OPAQUE_ORIGIN.equals(origin)) {
            return;
        }
        String host;
        try {
            host = URI.create(origin.trim()).getHost();
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("origin_mismatch", "malformed origin");
        }
        if (host != null && !Domains.normalize(host).equals(normalizedDomain)) {
            throw new InvalidEventException("origin_mismatch", "origin does not match the tracked domain");
        }
    }

    private static UriComponents parse(String url) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if (components.getHost() == null) {
                throw new InvalidEventException("invalid_url", "url must be absolute");
            }
            return components;
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("invalid_url", "malformed url");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
