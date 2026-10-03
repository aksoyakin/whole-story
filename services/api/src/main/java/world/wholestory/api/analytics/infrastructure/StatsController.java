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
import world.wholestory.api.analytics.application.GoalConversion;
import world.wholestory.api.analytics.application.Interval;
import world.wholestory.api.analytics.application.RealtimeVisitors;
import world.wholestory.api.analytics.application.SiteNotVisibleException;
import world.wholestory.api.analytics.application.StatsQueries;
import world.wholestory.api.shared.security.AuthenticatedUser;
import world.wholestory.api.site.ReadableGoal;
import world.wholestory.api.site.ReadableSite;
import world.wholestory.api.site.SiteAccess;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Dashboard queries. The dates are the site's own: {@code from} and {@code to} are local days, resolved with the
 * timezone Site Management holds for the site, so everyone looking at one site sees the same day boundaries.
 */
@RestController
@RequestMapping("/api/sites/{siteId}/stats")
@RequiredArgsConstructor
class StatsController {

    private final StatsQueries stats;
    private final RealtimeVisitors realtimeVisitors;
    private final SiteAccess sites;

    @GetMapping("/summary")
    SummaryResponse summary(@PathVariable UUID siteId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(name = "filter", required = false) List<String> filter,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return StatsResponseMapper.toResponse(
                stats.summary(siteId, rangeFor(siteId, principal, from, to), StatsRequests.parseFilters(filter)));
    }

    /**
     * Who is on the site right now. No date range and no filter: this one is not a report over stored events
     * but a count the processor keeps in Redis as they arrive.
     */
    @GetMapping("/realtime")
    RealtimeResponse realtime(@PathVariable UUID siteId, @AuthenticationPrincipal AuthenticatedUser principal) {
        sites.readableBy(siteId, principal.getUserId()).orElseThrow(SiteNotVisibleException::new);
        return new RealtimeResponse(realtimeVisitors.on(siteId));
    }

    /** Buckets are hourly for a single day and daily for anything longer, unless the caller says otherwise. */
    @GetMapping("/timeseries")
    List<TimeseriesPointResponse> timeseries(@PathVariable UUID siteId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Interval interval,
                                             @RequestParam(name = "filter", required = false) List<String> filter,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        DateRange range = rangeFor(siteId, principal, from, to);
        Interval buckets = interval == null ? range.naturalInterval() : interval;
        return stats.timeseries(siteId, range, buckets, StatsRequests.parseFilters(filter)).stream()
                .map(StatsResponseMapper::toResponse)
                .toList();
    }

    /**
     * How each of this site's goals did. The definitions belong to Site Management, so they are fetched from it
     * and translated into what the read side evaluates; a site with no goals answers with an empty list rather
     * than an error, because having none is an ordinary state.
     */
    @GetMapping("/goals")
    List<GoalConversionResponse> goals(@PathVariable UUID siteId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(name = "filter", required = false) List<String> filter,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        DateRange range = rangeFor(siteId, principal, from, to);
        List<ReadableGoal> defined = sites.goalsOf(siteId);
        List<GoalConversion> conversions =
                stats.goals(siteId, range, defined.stream().map(StatsRequests::toDefinition).toList(), StatsRequests.parseFilters(filter));
        return IntStream.range(0, conversions.size())
                .mapToObj(i -> StatsResponseMapper.toResponse(conversions.get(i), defined.get(i)))
                .toList();
    }

    @GetMapping("/breakdown")
    List<BreakdownEntryResponse> breakdown(@PathVariable UUID siteId,
                                           @RequestParam Dimension dimension,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @RequestParam(name = "filter", required = false) List<String> filter,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        DateRange range = rangeFor(siteId, principal, from, to);
        int size = StatsRequests.breakdownSize(limit);
        return stats.breakdown(siteId, range, dimension, size, StatsRequests.parseFilters(filter)).stream()
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
