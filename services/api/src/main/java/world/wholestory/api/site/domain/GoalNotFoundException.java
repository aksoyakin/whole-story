package world.wholestory.api.site.domain;

import world.wholestory.api.shared.domain.NotFoundException;

/** Also raised for a goal of someone else's site, so the two cannot be told apart. */
public class GoalNotFoundException extends NotFoundException {

    public GoalNotFoundException() {
        super("no such goal");
    }
}
