package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.site.domain.Goal;
import world.wholestory.api.site.domain.GoalId;
import world.wholestory.api.site.domain.GoalNotFoundException;
import world.wholestory.api.site.domain.Site;

import java.util.UUID;

/**
 * Stops counting something as a conversion. The events stay: a goal is a question, and removing it only stops
 * the question being asked.
 */
@Service
@RequiredArgsConstructor
public class RemoveGoal {

    private final GoalRepository goals;
    private final ReadableSites readableSites;

    @Transactional
    public void remove(UUID siteId, UUID goalId, UUID actingUserId) {
        Site site = readableSites.require(siteId, actingUserId);
        Goal goal = goals.findById(GoalId.of(goalId)).orElseThrow(GoalNotFoundException::new);
        // A goal of another site answers as one that does not exist, even when that site is also the caller's.
        if (!goal.belongsTo(site.getId())) {
            throw new GoalNotFoundException();
        }
        goals.remove(goal.getId());
    }
}
