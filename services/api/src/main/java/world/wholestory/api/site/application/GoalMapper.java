package world.wholestory.api.site.application;

import world.wholestory.api.site.ReadableGoal;
import world.wholestory.api.site.domain.Goal;

/** Hand-written, like every mapper in this project. */
final class GoalMapper {

    private GoalMapper() {
    }

    static GoalSummary toSummary(Goal goal) {
        return new GoalSummary(
                goal.getId().value(),
                goal.getTarget().type(),
                goal.getTarget().value(),
                goal.getCreatedAt());
    }

    /** The shape other contexts see: the type travels as a string, since Analytics holds no enum of ours. */
    static ReadableGoal toReadable(Goal goal) {
        return new ReadableGoal(
                goal.getId().value(),
                goal.getTarget().type().name(),
                goal.getTarget().value());
    }
}
