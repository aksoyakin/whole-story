package world.wholestory.api.analytics.application;

import java.util.UUID;

/**
 * A goal as this side needs to evaluate it. Its own type rather than Site Management's {@code ReadableGoal}, so
 * the read-side port does not take another module's vocabulary — the web adapter maps between them, as it
 * already does to turn a site's timezone into a {@link DateRange}.
 *
 * @param target the event name, or a path pattern in which {@code *} matches any part of a path
 */
public record GoalDefinition(UUID goalId, Kind kind, String target) {

    public enum Kind {
        EVENT,
        PAGE
    }
}
