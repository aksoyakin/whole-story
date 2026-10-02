package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.site.domain.Goal;
import world.wholestory.api.site.domain.GoalId;
import world.wholestory.api.site.domain.GoalTarget;
import world.wholestory.api.site.domain.GoalType;
import world.wholestory.api.site.domain.Site;
import world.wholestory.contracts.UuidV7;

import java.time.Clock;
import java.time.Instant;

/**
 * Starts counting something as a conversion.
 * <p>
 * Nothing is published. A goal changes nothing about what is collected, so ingest and processor have no reason
 * to hear about it — which is also why a goal defined today reports on events from last month.
 */
@Service
@RequiredArgsConstructor
public class DefineGoal {

    private final GoalRepository goals;
    private final ReadableSites readableSites;
    private final Clock clock;

    @Transactional
    public GoalSummary define(DefineGoalCommand command) {
        Site site = readableSites.require(command.siteId(), command.actingUserId());
        GoalTarget target = command.type() == GoalType.EVENT
                ? GoalTarget.event(command.target())
                : GoalTarget.page(command.target());

        Instant now = clock.instant();
        Goal goal = Goal.define(GoalId.of(UuidV7.generate(now)), site.getId(), target, now);
        goals.save(goal);
        return GoalMapper.toSummary(goal);
    }
}
