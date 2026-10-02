package world.wholestory.api.site.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * Something a site owner counts as a conversion.
 * <p>
 * Its own aggregate rather than a part of {@link Site}, decided on the invariant question: no rule needs to see
 * all of a site's goals at once, since uniqueness of the target is the index's job as it is for a domain. The
 * cost of the alternative is concrete — {@code SiteAccess.readableBy} runs on every reporting request, and a
 * single dashboard makes ten of them, so goals hanging off the site would be read every time for no rule.
 * <p>
 * Nothing in the pipeline hears about a goal, which is why there is no domain event here. Unlike a site, a goal
 * changes nothing about what is collected: it is a question asked of events that were already being recorded,
 * and defining one today says something about last month as well.
 */
@Getter
public final class Goal {

    private final GoalId id;
    private final SiteId siteId;
    private final GoalTarget target;
    private final Instant createdAt;

    private Goal(GoalId id, SiteId siteId, GoalTarget target, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.target = Objects.requireNonNull(target);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Goal define(GoalId id, SiteId siteId, GoalTarget target, Instant now) {
        return new Goal(id, siteId, target, now);
    }

    /** Rebuilds a stored goal. For persistence adapters only. */
    public static Goal restore(GoalId id, SiteId siteId, GoalTarget target, Instant createdAt) {
        return new Goal(id, siteId, target, createdAt);
    }

    public boolean belongsTo(SiteId candidate) {
        return siteId.equals(candidate);
    }
}
