package world.wholestory.ingest.collect;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import world.wholestory.contracts.RawEventV1;
import world.wholestory.contracts.UuidV7;
import world.wholestory.ingest.privacy.VisitorHasher;
import world.wholestory.ingest.publish.RawEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class EventCollector {

    private static final int MAX_NAME_LENGTH = 120;

    private final SiteRegistry siteRegistry;
    private final VisitorHasher visitorHasher;
    private final RawEventPublisher publisher;
    private final Clock clock;

    /**
     * Turns a tracker payload into a privacy-safe event. The IP address is only used for hashing
     * (and GeoIP in M2) and is dropped when this method returns.
     */
    void collect(IncomingEvent incoming, String ipAddress, String userAgent) {
        if (incoming.name() == null || incoming.name().isBlank() || incoming.name().length() > MAX_NAME_LENGTH) {
            throw new InvalidEventException("invalid event name");
        }
        if (incoming.domain() == null || incoming.url() == null) {
            throw new InvalidEventException("domain and url are required");
        }
        UUID siteId = siteRegistry.findSiteId(Domains.normalize(incoming.domain()))
                .orElseThrow(() -> new InvalidEventException("unknown domain"));
        UriComponents url = parse(incoming.url());

        Instant now = clock.instant();
        String agent = userAgent == null ? "" : userAgent;
        long visitorHash = visitorHasher.hash(now, siteId, ipAddress, agent);

        publisher.publish(new RawEventV1(
                RawEventV1.SCHEMA_VERSION,
                UuidV7.generate(now),
                now,
                siteId,
                visitorHash,
                null,
                incoming.name(),
                url.getHost(),
                url.getPath() == null || url.getPath().isEmpty() ? "/" : url.getPath(),
                blankToNull(incoming.referrer()),
                url.getQueryParams().getFirst("utm_source"),
                url.getQueryParams().getFirst("utm_medium"),
                url.getQueryParams().getFirst("utm_campaign"),
                url.getQueryParams().getFirst("utm_content"),
                url.getQueryParams().getFirst("utm_term"),
                null,
                null,
                null,
                agent,
                incoming.props()));
    }

    private static UriComponents parse(String url) {
        try {
            UriComponents components = UriComponentsBuilder.fromUriString(url).build();
            if (components.getHost() == null) {
                throw new InvalidEventException("url must be absolute");
            }
            return components;
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("malformed url");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
