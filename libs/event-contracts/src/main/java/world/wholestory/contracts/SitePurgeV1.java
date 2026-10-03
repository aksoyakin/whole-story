package world.wholestory.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything collected for a site may go. Published by Site Management when a site is removed and consumed by
 * the processor, which owns the analytics schema and is therefore the only service allowed to delete from it.
 * <p>
 * Keyed on the site id rather than the domain, on a log compacted topic. A purge is work that has to happen
 * once, not a statement about the current state of a domain, and those need different durability: a
 * retention-based topic would drop the record if the processor were away long enough, while compacting on the
 * domain would let a later registration of the same name erase it. A site id is never reused, so one record per
 * site survives for as long as the topic does.
 *
 * @param occurredAt when the site was removed, not when the purge ran
 */
public record SitePurgeV1(int schemaVersion, UUID siteId, Instant occurredAt) {

    public static final int SCHEMA_VERSION = 1;

    public static SitePurgeV1 of(UUID siteId, Instant occurredAt) {
        return new SitePurgeV1(SCHEMA_VERSION, siteId, occurredAt);
    }
}
