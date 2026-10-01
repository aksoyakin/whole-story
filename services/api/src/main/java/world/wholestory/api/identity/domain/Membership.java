package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/** A user's place in an organization. Lives inside the {@link Organization} aggregate, never on its own. */
public record Membership(UserId userId, Role role, Instant createdAt) {

    public Membership {
        Objects.requireNonNull(userId, "a membership needs a user");
        Objects.requireNonNull(role, "a membership needs a role");
        Objects.requireNonNull(createdAt, "a membership needs a creation time");
    }

    boolean isOwner() {
        return role == Role.OWNER;
    }

    Membership withRole(Role newRole) {
        return new Membership(userId, newRole, createdAt);
    }
}
