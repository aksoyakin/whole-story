package world.wholestory.api.site.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import world.wholestory.api.site.application.GoalRepository;
import world.wholestory.api.site.domain.Goal;
import world.wholestory.api.site.domain.GoalAlreadyDefinedException;
import world.wholestory.api.site.domain.GoalId;
import world.wholestory.api.site.domain.SiteId;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JpaGoalRepository implements GoalRepository {

    /** The unique index on sites.goals(site_id, type, coalesce(event_name, page_path)). */
    private static final String TARGET_UNIQUE_INDEX = "goals_site_target_uq";

    private final GoalEntityRepository goals;

    @Override
    public void save(Goal goal) {
        try {
            goals.saveAndFlush(GoalJpaMapper.toEntity(goal));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateTarget(e)) {
                throw new GoalAlreadyDefinedException(goal.getTarget());
            }
            throw e;
        }
    }

    @Override
    public Optional<Goal> findById(GoalId id) {
        return goals.findById(id.value()).map(GoalJpaMapper::toDomain);
    }

    @Override
    public List<Goal> findBySite(SiteId siteId) {
        return goals.findBySiteIdOrderByCreatedAt(siteId.value()).stream().map(GoalJpaMapper::toDomain).toList();
    }

    @Override
    public void remove(GoalId id) {
        goals.deleteById(id.value());
    }

    private static boolean isDuplicateTarget(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(TARGET_UNIQUE_INDEX);
    }
}
