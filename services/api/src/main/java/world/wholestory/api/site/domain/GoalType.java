package world.wholestory.api.site.domain;

/**
 * The two kinds of thing a site owner can call a conversion. The names match the values the check constraint on
 * {@code sites.goals} allows, so the enum and the table cannot drift apart silently.
 */
public enum GoalType {

    /** A custom event the tracker reports by name, e.g. {@code wholestory("Signup")}. */
    EVENT,
    /** Reaching a page, matched against the path a pageview was recorded for. */
    PAGEVIEW
}
