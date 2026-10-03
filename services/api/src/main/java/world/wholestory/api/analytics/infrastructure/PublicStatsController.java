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
import world.wholestory.api.analytics.application.Dimension;
import world.wholestory.api.analytics.application.GoalConversion;
import world.wholestory.api.analytics.application.Interval;
import world.wholestory.api.analytics.application.RealtimeVisitors;
import world.wholestory.api.analytics.application.SiteNotVisibleException;
import world.wholestory.api.analytics.application.StatsQueries;
import world.wholestory.api.site.ReadableGoal;
import world.wholestory.api.site.ReadableSite;
import world.wholestory.api.site.SiteAccess;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The same reports, for a dashboard its owner chose to make public.
 * <p>
 * Separate from {@link StatsController} because the two differ in exactly one thing and it is the one that
 * matters: who is allowed to read. There the question is membership of the owning organization, here it is
 * whether sharing is switched on, and a site is named by its domain rather than by an id because that is what
 * the shared URL carries. Everything after that — the filters, the limits, the queries — is the same code, so
 * a shared dashboard cannot drift away from the one its owner sees.
 * <p>
 * Nothing here reads a principal, and nothing here tells the caller why it said no: a site that is not shared,
 * one that was removed and one that never existed all answer alike.
 */
@RestController
@RequestMapping("/api/public/sites/{domain}/stats")
@RequiredArgsConstructor
class PublicStatsController {

    private final StatsQueries stats;
    private final RealtimeVisitors realtimeVisitors;
    private final SiteAccess sites;

    @GetMapping("/summary")
    SummaryResponse summary(@PathVariable String domain,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(name = "filter", required = false) List<String> filter) {
        ReadableSite site = shared(domain);
        return StatsResponseMapper.toResponse(
                stats.summary(site.siteId(), rangeOf(site, from, to), StatsRequests.parseFilters(filter)));
    }

    @GetMapping("/realtime")
    RealtimeResponse realtime(@PathVariable String domain) {
        return new RealtimeResponse(realtimeVisitors.on(shared(domain).siteId()));
    }

    @GetMapping("/timeseries")
    List<TimeseriesPointResponse> timeseries(@PathVariable String domain,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Interval interval,
                                             @RequestParam(name = "filter", required = false) List<String> filter) {
        ReadableSite site = shared(domain);
        DateRange range = rangeOf(site, from, to);
        Interval buckets = interval == null ? range.naturalInterval() : interval;
        return stats.timeseries(site.siteId(), range, buckets, StatsRequests.parseFilters(filter)).stream()
                .map(StatsResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/breakdown")
    List<BreakdownEntryResponse> breakdown(@PathVariable String domain,
                                           @RequestParam Dimension dimension,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "10") int limit,
                                           @RequestParam(name = "filter", required = false) List<String> filter) {
        ReadableSite site = shared(domain);
        return stats.breakdown(site.siteId(), rangeOf(site, from, to), dimension,
                        StatsRequests.breakdownSize(limit), StatsRequests.parseFilters(filter)).stream()
                .map(StatsResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/goals")
    List<GoalConversionResponse> goals(@PathVariable String domain,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(name = "filter", required = false) List<String> filter) {
        ReadableSite site = shared(domain);
        List<ReadableGoal> defined = sites.goalsOf(site.siteId());
        List<GoalConversion> conversions = stats.goals(site.siteId(), rangeOf(site, from, to),
                defined.stream().map(StatsRequests::toDefinition).toList(), StatsRequests.parseFilters(filter));
        return IntStream.range(0, conversions.size())
                .mapToObj(i -> StatsResponseMapper.toResponse(conversions.get(i), defined.get(i)))
                .toList();
    }

    /** Sharing switched off answers exactly as a domain nobody tracks, so the URL reveals neither. */
    private ReadableSite shared(String domain) {
        return sites.publiclyReadable(domain).orElseThrow(SiteNotVisibleException::new);
    }

    /** Days belong to the site here too: a shared dashboard shows the same boundaries as its owner's. */
    private static DateRange rangeOf(ReadableSite site, LocalDate from, LocalDate to) {
        return DateRange.of(from, to, site.timezone());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> badRequest() {
        return ResponseEntity.badRequest().build();
    }
}
