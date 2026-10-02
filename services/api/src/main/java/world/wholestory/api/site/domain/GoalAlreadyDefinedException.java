package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.AlreadyExistsException;

/**
 * A site already counts this as a conversion. Like every uniqueness rule here it is guaranteed by the index
 * rather than by an aggregate, and the persistence adapter raises this when the database refuses the insert.
 */
public class GoalAlreadyDefinedException extends AlreadyExistsException {

    public GoalAlreadyDefinedException(GoalTarget target) {
        super("already a goal of this site: " + target);
    }
}
