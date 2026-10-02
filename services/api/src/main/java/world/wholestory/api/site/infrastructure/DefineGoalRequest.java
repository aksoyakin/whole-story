package world.wholestory.api.site.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import world.wholestory.api.site.domain.GoalTarget;
import world.wholestory.api.site.domain.GoalType;

/**
 * @param target the event name for an {@code EVENT} goal, the path for a {@code PAGEVIEW} one. Shape is checked
 *               here; what makes a target usable is the domain's business.
 */
record DefineGoalRequest(
        @NotNull GoalType type,
        @NotBlank @Size(max = GoalTarget.MAX_PAGE_PATTERN_LENGTH) String target) {
}
