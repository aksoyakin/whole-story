package world.wholestory.api.site.infrastructure;

import java.time.Instant;
import java.util.UUID;

record GoalResponse(UUID goalId, String type, String target, Instant createdAt) {
}
