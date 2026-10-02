package world.wholestory.api.site;

import java.util.UUID;

/**
 * A goal as the reporting side needs it: what to look for, and under which id to report the answer.
 *
 * @param type   {@code EVENT} or {@code PAGEVIEW}, as a plain string — Analytics has no domain model to hold an
 *               enum of this context (D-089)
 * @param target the event name, or the page pattern in which {@code *} matches any part of a path
 */
public record ReadableGoal(UUID goalId, String type, String target) {
}
