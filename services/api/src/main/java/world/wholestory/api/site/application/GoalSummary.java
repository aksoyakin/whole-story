package world.wholestory.api.site.application;

import world.wholestory.api.site.domain.GoalType;

import java.time.Instant;
import java.util.UUID;

/**
 * What it takes to list a goal.
 *
 * @param target the event name or the page pattern, whichever this goal's type calls for
 */
public record GoalSummary(UUID goalId, GoalType type, String target, Instant createdAt) {
}
