package world.wholestory.api.site.infrastructure;

import world.wholestory.api.site.domain.Goal;
import world.wholestory.api.site.domain.GoalId;
import world.wholestory.api.site.domain.GoalTarget;
import world.wholestory.api.site.domain.GoalType;
import world.wholestory.api.site.domain.SiteId;

final class GoalJpaMapper {

    private GoalJpaMapper() {
    }

    static GoalJpaEntity toEntity(Goal goal) {
        GoalTarget target = goal.getTarget();
        GoalJpaEntity entity = new GoalJpaEntity();
        entity.setId(goal.getId().value());
        entity.setSiteId(goal.getSiteId().value());
        entity.setType(target.type());
        // Exactly one of the two columns is filled, which is what the table's check constraint demands.
        entity.setEventName(target.isPage() ? null : target.value());
        entity.setPagePath(target.isPage() ? target.value() : null);
        entity.setCreatedAt(goal.getCreatedAt());
        return entity;
    }

    static Goal toDomain(GoalJpaEntity entity) {
        GoalTarget target = entity.getType() == GoalType.PAGEVIEW
                ? GoalTarget.page(entity.getPagePath())
                : GoalTarget.event(entity.getEventName());
        return Goal.restore(
                GoalId.of(entity.getId()),
                SiteId.of(entity.getSiteId()),
                target,
                entity.getCreatedAt());
    }
}
