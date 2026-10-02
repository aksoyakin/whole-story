package world.wholestory.api.site.infrastructure;

import world.wholestory.api.site.application.GoalSummary;

final class GoalResponseMapper {

    private GoalResponseMapper() {
    }

    static GoalResponse toResponse(GoalSummary summary) {
        return new GoalResponse(
                summary.goalId(),
                summary.type().name(),
                summary.target(),
                summary.createdAt());
    }
}
