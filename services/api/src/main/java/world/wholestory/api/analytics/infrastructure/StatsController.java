package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.StatsQueries;

import java.time.LocalDate;
import java.util.UUID;

// TODO(M3): authorise access to the site once Identity & Access exists.
@RestController
@RequestMapping("/api/sites/{siteId}/stats")
@RequiredArgsConstructor
class StatsController {

    private final StatsQueries stats;

    @GetMapping("/aggregate")
    AggregateStatsResponse aggregate(@PathVariable UUID siteId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return StatsResponseMapper.toResponse(stats.aggregate(siteId, new DateRange(from, to)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> badRequest() {
        return ResponseEntity.badRequest().build();
    }
}
