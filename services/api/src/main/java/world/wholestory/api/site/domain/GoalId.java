package world.wholestory.api.site.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a goal. Stays in this context, like {@link SiteId}: no other context has to name one. */
public record GoalId(UUID value) {

    public GoalId {
        Objects.requireNonNull(value, "a goal id is required");
    }

    public static GoalId of(UUID value) {
        return new GoalId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
