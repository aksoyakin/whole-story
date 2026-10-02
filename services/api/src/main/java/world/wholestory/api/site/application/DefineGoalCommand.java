package world.wholestory.api.site.application;

import world.wholestory.api.site.domain.GoalType;

import java.util.UUID;

public record DefineGoalCommand(UUID siteId, UUID actingUserId, GoalType type, String target) {
}
