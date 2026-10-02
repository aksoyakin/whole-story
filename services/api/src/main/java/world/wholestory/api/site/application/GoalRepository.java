package world.wholestory.api.site.application;

import world.wholestory.api.site.domain.Goal;
import world.wholestory.api.site.domain.GoalId;
import world.wholestory.api.site.domain.SiteId;

import java.util.List;
import java.util.Optional;

/**
 * Port to the goals of a site. The adapter turns a violated uniqueness index into
 * {@code GoalAlreadyDefinedException}: one target belongs to one goal per site, which no aggregate can see.
 */
public interface GoalRepository {

    void save(Goal goal);

    Optional<Goal> findById(GoalId id);

    List<Goal> findBySite(SiteId siteId);

    void remove(GoalId id);
}
