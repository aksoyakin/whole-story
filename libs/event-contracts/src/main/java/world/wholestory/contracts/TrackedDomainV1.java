package world.wholestory.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Whether a domain is tracked, and by which site. One record per domain, published by Site Management through
 * its outbox and consumed by ingest to decide what it accepts.
 * <p>
 * State rather than a change, because the topic is log compacted: the last record for a domain is the whole
 * truth about it, so a consumer with an empty cache can rebuild from the log alone. One self-describing type
 * instead of a registered/removed pair also keeps the contracts free of type discriminators — a consumer reads
 * every record the same way.
 *
 * @param tracked false once the site was removed; the domain may then be registered again, by anyone
 */
public record TrackedDomainV1(
        int schemaVersion,
        String domain,
        UUID siteId,
        UUID organizationId,
        boolean tracked,
        Instant occurredAt
) {

    public static final int SCHEMA_VERSION = 1;

    public static TrackedDomainV1 tracked(String domain, UUID siteId, UUID organizationId, Instant occurredAt) {
        return new TrackedDomainV1(SCHEMA_VERSION, domain, siteId, organizationId, true, occurredAt);
    }

    public static TrackedDomainV1 untracked(String domain, UUID siteId, Instant occurredAt) {
        return new TrackedDomainV1(SCHEMA_VERSION, domain, siteId, null, false, occurredAt);
    }
}
