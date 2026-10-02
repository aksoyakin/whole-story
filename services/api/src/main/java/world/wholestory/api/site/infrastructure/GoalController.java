package world.wholestory.api.site.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.shared.security.AuthenticatedUser;
import world.wholestory.api.site.application.DefineGoal;
import world.wholestory.api.site.application.DefineGoalCommand;
import world.wholestory.api.site.application.ListGoals;
import world.wholestory.api.site.application.RemoveGoal;

import java.util.List;
import java.util.UUID;

/**
 * Defining what counts as a conversion. The conversions themselves are a report and live under
 * {@code /stats/goals}: this is the site's configuration, that is a question about its events.
 */
@RestController
@RequestMapping("/api/sites/{siteId}/goals")
@RequiredArgsConstructor
class GoalController {

    private final DefineGoal defineGoal;
    private final ListGoals listGoals;
    private final RemoveGoal removeGoal;

    @PostMapping
    ResponseEntity<GoalResponse> define(@PathVariable UUID siteId,
                                        @Valid @RequestBody DefineGoalRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        GoalResponse response = GoalResponseMapper.toResponse(defineGoal.define(
                new DefineGoalCommand(siteId, principal.getUserId(), request.type(), request.target())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    List<GoalResponse> list(@PathVariable UUID siteId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return listGoals.of(siteId, principal.getUserId()).stream()
                .map(GoalResponseMapper::toResponse)
                .toList();
    }

    @DeleteMapping("/{goalId}")
    ResponseEntity<Void> remove(@PathVariable UUID siteId,
                                @PathVariable UUID goalId,
                                @AuthenticationPrincipal AuthenticatedUser principal) {
        removeGoal.remove(siteId, goalId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
