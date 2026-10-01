package world.wholestory.api.identity.domain;

import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The tenant: sites and members belong to an organization (D-003). In v1 every registration creates a
 * single-person one that the interface never shows, which is why the aggregate is built for members and roles
 * from the start — team management is a later feature, not a later migration (D-004).
 * <p>
 * The consistency boundary is the membership list: "an organization always keeps at least one owner" can only be
 * guaranteed by the object that holds every membership.
 */
@Getter
public final class Organization {

    public static final int DEFAULT_SITE_LIMIT = 10;
    public static final long DEFAULT_MONTHLY_EVENT_LIMIT = 1_000_000L;

    private final OrganizationId id;
    private PersonName name;
    private int siteLimit;
    private long monthlyEventLimit;
    /** Keyed by user so that a membership cannot be duplicated; insertion ordered to keep the owner first. */
    @Getter(AccessLevel.NONE)
    private final Map<UserId, Membership> memberships;
    private final Instant createdAt;
    private Instant updatedAt;

    private Organization(OrganizationId id, PersonName name, int siteLimit, long monthlyEventLimit,
                         Map<UserId, Membership> memberships, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.siteLimit = siteLimit;
        this.monthlyEventLimit = monthlyEventLimit;
        this.memberships = new LinkedHashMap<>(Objects.requireNonNull(memberships));
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /** A fresh organization for the person who just registered, who becomes its owner. */
    public static Organization createFor(OrganizationId id, UserId owner, PersonName name, Instant now) {
        Map<UserId, Membership> memberships = new LinkedHashMap<>();
        memberships.put(owner, new Membership(owner, Role.OWNER, now));
        return new Organization(id, name, DEFAULT_SITE_LIMIT, DEFAULT_MONTHLY_EVENT_LIMIT, memberships, now, now);
    }

    /** Rebuilds a stored organization. For persistence adapters only. */
    public static Organization restore(OrganizationId id, PersonName name, int siteLimit, long monthlyEventLimit,
                                       Collection<Membership> memberships, Instant createdAt, Instant updatedAt) {
        Map<UserId, Membership> byUser = new LinkedHashMap<>();
        memberships.forEach(membership -> byUser.put(membership.userId(), membership));
        return new Organization(id, name, siteLimit, monthlyEventLimit, byUser, createdAt, updatedAt);
    }

    public void addMember(UserId userId, Role role, Instant now) {
        if (memberships.containsKey(userId)) {
            throw MembershipException.alreadyAMember(userId);
        }
        memberships.put(userId, new Membership(userId, role, now));
        this.updatedAt = now;
    }

    public void removeMember(UserId userId, Instant now) {
        Membership membership = require(userId);
        if (isLastOwner(membership)) {
            throw MembershipException.lastOwner(userId);
        }
        memberships.remove(userId);
        this.updatedAt = now;
    }

    public void changeRole(UserId userId, Role newRole, Instant now) {
        Membership membership = require(userId);
        if (membership.role() == newRole) {
            return;
        }
        if (isLastOwner(membership)) {
            throw MembershipException.lastOwner(userId);
        }
        memberships.put(userId, membership.withRole(newRole));
        this.updatedAt = now;
    }

    public void rename(PersonName newName, Instant now) {
        this.name = Objects.requireNonNull(newName);
        this.updatedAt = now;
    }

    /** Limits come from the organization's plan, so only the organization may change them. */
    public void applyLimits(int newSiteLimit, long newMonthlyEventLimit, Instant now) {
        if (newSiteLimit < 0 || newMonthlyEventLimit < 0) {
            throw new IllegalArgumentException("limits cannot be negative");
        }
        this.siteLimit = newSiteLimit;
        this.monthlyEventLimit = newMonthlyEventLimit;
        this.updatedAt = now;
    }

    /** @return true when another site still fits in this organization's plan */
    public boolean allowsAnotherSite(int currentSiteCount) {
        return currentSiteCount < siteLimit;
    }

    public Optional<Role> roleOf(UserId userId) {
        return Optional.ofNullable(memberships.get(userId)).map(Membership::role);
    }

    public boolean hasMember(UserId userId) {
        return memberships.containsKey(userId);
    }

    public List<Membership> memberships() {
        return List.copyOf(memberships.values());
    }

    private Membership require(UserId userId) {
        Membership membership = memberships.get(userId);
        if (membership == null) {
            throw MembershipException.notAMember(userId);
        }
        return membership;
    }

    private boolean isLastOwner(Membership membership) {
        return membership.isOwner() && memberships.values().stream().filter(Membership::isOwner).count() == 1;
    }
}
