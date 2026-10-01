package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.SiteNotVisibleException;
import world.wholestory.api.analytics.application.StatsQueries;
import world.wholestory.api.shared.security.AuthenticatedUser;
import world.wholestory.api.site.SiteAccess;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/sites/{siteId}/stats")
@RequiredArgsConstructor
class StatsController {

    private final StatsQueries stats;
    private final SiteAccess sites;

    @GetMapping("/aggregate")
    AggregateStatsResponse aggregate(@PathVariable UUID siteId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @AuthenticationPrincipal AuthenticatedUser principal) {
        // Whether this person may see the site is Site Management's answer, not a query parameter.
        if (!sites.canRead(siteId, principal.getUserId())) {
            throw new SiteNotVisibleException();
        }
        return StatsResponseMapper.toResponse(stats.aggregate(siteId, new DateRange(from, to)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> badRequest() {
        return ResponseEntity.badRequest().build();
    }
}
