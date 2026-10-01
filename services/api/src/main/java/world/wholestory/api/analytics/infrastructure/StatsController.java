package world.wholestory.api.analytics.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.Dimension;
import world.wholestory.api.analytics.application.Interval;
import world.wholestory.api.analytics.application.SiteNotVisibleException;
import world.wholestory.api.analytics.application.StatsQueries;
import world.wholestory.api.shared.security.AuthenticatedUser;
import world.wholestory.api.site.ReadableSite;
import world.wholestory.api.site.SiteAccess;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard queries. The dates are the site's own: {@code from} and {@code to} are local days, resolved with the
 * timezone Site Management holds for the site, so everyone looking at one site sees the same day boundaries.
 */
@RestController
@RequestMapping("/api/sites/{siteId}/stats")
@RequiredArgsConstructor
class StatsController {

    /** Enough for any list a dashboard shows, and a ceiling on what one request can ask the database for. */
    private static final int MAX_BREAKDOWN_SIZE = 100;

    private final StatsQueries stats;
    private final SiteAccess sites;

    @GetMapping("/summary")
    SummaryResponse summary(@PathVariable UUID siteId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return StatsResponseMapper.toResponse(stats.summary(siteId, rangeFor(siteId, principal, from, to)));
    }

    /** Buckets are hourly for a single day and daily for anything longer, unless the caller says otherwise. */
    @GetMapping("/timeseries")
    List<TimeseriesPointResponse> timeseries(@PathVariable UUID siteId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Interval interval,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        DateRange range = rangeFor(siteId, principal, from, to);
        Interval buckets = interval == null ? range.naturalInterval() : interval;
        return stats.timeseries(siteId, range, buckets).stream().map(StatsResponseMapper::toResponse).toList();
    }

    @GetMapping("/breakdown")
    List<BreakdownEntryResponse> breakdown(@PathVariable UUID siteId,
                                           @RequestParam Dimension dimension,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        DateRange range = rangeFor(siteId, principal, from, to);
        int size = Math.clamp(limit, 1, MAX_BREAKDOWN_SIZE);
        return stats.breakdown(siteId, range, dimension, size).stream()
                .map(StatsResponseMapper::toResponse)
                .toList();
    }

    /** Authorization and the site's timezone in one answer; a site nobody may read answers like a missing one. */
    private DateRange rangeFor(UUID siteId, AuthenticatedUser principal, LocalDate from, LocalDate to) {
        ReadableSite site = sites.readableBy(siteId, principal.getUserId())
                .orElseThrow(SiteNotVisibleException::new);
        return DateRange.of(from, to, site.timezone());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> badRequest() {
        return ResponseEntity.badRequest().build();
    }
}
