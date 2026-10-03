package world.wholestory.api.site.domain;

import lombok.AccessLevel;
import lombok.Getter;
import world.wholestory.api.shared.domain.DomainEvent;
import world.wholestory.api.shared.domain.OrganizationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A tracked website, owned by an organization (D-003). Registering and removing one are the two things that
 * ingest has to hear about, so the aggregate records them as events and the application layer publishes them
 * once the change is committed (ADR 0009).
 * <p>
 * Removal is a soft delete (D-034): the domain frees up immediately, while the collected data is purged
 * afterwards by a background job.
 */
@Getter
public final class Site {

    private final SiteId id;
    private final OrganizationId organizationId;
    private final Domain domain;
    private Timezone timezone;
    private boolean publicDashboard;
    private final Instant createdAt;
    private Instant updatedAt;
    @Getter(AccessLevel.NONE)
    private Instant deletedAt;

    @Getter(AccessLevel.NONE)
    private final List<DomainEvent> recordedEvents = new ArrayList<>();

    private Site(SiteId id, OrganizationId organizationId, Domain domain, Timezone timezone,
                 boolean publicDashboard, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = Objects.requireNonNull(id);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.domain = Objects.requireNonNull(domain);
        this.timezone = Objects.requireNonNull(timezone);
        this.publicDashboard = publicDashboard;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.deletedAt = deletedAt;
    }

    public static Site register(SiteId id, OrganizationId organizationId, Domain domain, Timezone timezone,
                                Instant now) {
        Site site = new Site(id, organizationId, domain, timezone, false, now, now, null);
        site.recordedEvents.add(new SiteRegistered(id, organizationId, domain, now));
        return site;
    }

    /** Rebuilds a stored site. For persistence adapters only; records no event. */
    public static Site restore(SiteId id, OrganizationId organizationId, Domain domain, Timezone timezone,
                               boolean publicDashboard, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        return new Site(id, organizationId, domain, timezone, publicDashboard, createdAt, updatedAt, deletedAt);
    }

    /**
     * Moves where this site's day starts and ends.
     * <p>
     * Nothing stored has to move with it: days are cut at query time rather than at write time (D-020), so the
     * dashboard simply reports every past period in the new zone from the next request onwards. That is also the
     * cost of changing it — yesterday's numbers can shift by a few hours' worth of visits.
     */
    public void changeTimezone(Timezone newTimezone, Instant now) {
        Objects.requireNonNull(newTimezone, "a site needs a timezone");
        if (newTimezone.equals(timezone)) {
            return;
        }
        this.timezone = newTimezone;
        this.updatedAt = now;
    }

    /**
     * Opens the read-only dashboard to anyone holding the link.
     * <p>
     * No event is recorded. Unlike registering or removing a site, sharing changes nothing about what is
     * collected, so neither ingest nor processor has any reason to hear about it — the same reasoning that
     * keeps a goal eventless (D-122). Which site a visitor may read is answered when the link is followed.
     */
    public void enableSharing(Instant now) {
        if (publicDashboard) {
            return;
        }
        this.publicDashboard = true;
        this.updatedAt = now;
    }

    public void disableSharing(Instant now) {
        if (!publicDashboard) {
            return;
        }
        this.publicDashboard = false;
        this.updatedAt = now;
    }

    /** Idempotent: removing a site twice must not announce it twice, because ingest acts on every message. */
    public void remove(Instant now) {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = now;
        this.updatedAt = now;
        this.publicDashboard = false;
        this.recordedEvents.add(new SiteRemoved(id, domain, now));
    }

    public boolean isRemoved() {
        return deletedAt != null;
    }

    public Optional<Instant> deletedAt() {
        return Optional.ofNullable(deletedAt);
    }

    /** Hands the recorded events over and forgets them, so they cannot be published twice. */
    public List<DomainEvent> pullRecordedEvents() {
        List<DomainEvent> events = List.copyOf(recordedEvents);
        recordedEvents.clear();
        return events;
    }
}
