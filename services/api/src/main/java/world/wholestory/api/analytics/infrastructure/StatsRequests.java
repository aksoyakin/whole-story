package world.wholestory.api.analytics.infrastructure;

import world.wholestory.api.analytics.application.DateRange;
import world.wholestory.api.analytics.application.Dimension;
import world.wholestory.api.analytics.application.Filter;
import world.wholestory.api.analytics.application.GoalDefinition;
import world.wholestory.api.site.ReadableGoal;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Reading what a reporting request asks for. Shared by the dashboard's endpoints and the public ones, so that
 * a shared dashboard cannot end up with slightly different rules from the one its owner sees.
 */
final class StatsRequests {

    /** Enough for any list a dashboard shows, and a ceiling on what one request can ask the database for. */
    static final int MAX_BREAKDOWN_SIZE = 100;
    /** More than one filter per dimension is already unusual; this only stops an absurd request. */
    private static final int MAX_FILTERS = 10;
    /**
     * The longest range one request may ask for.
     * <p>
     * The same kind of ceiling as the two above, and the one that matters most: these endpoints have a public
     * half that answers without a session (D-137), so the cost of a request is not bounded by anyone having an
     * account. A twelve-month report is 24 seconds of measured database work and grows with the range, so
     * without this a shared link is a way to ask for minutes of it.
     * <p>
     * 366 rather than a rounder number: a year plus the leap day, so "the last year" always fits. It is also
     * the longest range that has been measured end to end, and a limit you have measured is one you can
     * defend.
     */
    static final int MAX_RANGE_DAYS = 366;

    private StatsRequests() {
    }

    /**
     * The range a report covers, refused rather than quietly shortened when it is too long.
     * <p>
     * {@link #breakdownSize(int)} clamps because the size of a list is a presentation detail and nobody is
     * misled by getting a hundred rows. A date range is not that: somebody asked for a specific window, and
     * answering a different one without saying so is the kind of silently wrong number this codebase keeps
     * finding the expensive way.
     */
    static DateRange dateRange(LocalDate from, LocalDate to, String timezone, LocalDate retainedFrom) {
        DateRange range = DateRange.of(from, to, timezone);
        if (range.days() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("a range may cover at most " + MAX_RANGE_DAYS + " days");
        }
        requireWithinRetention(range, retainedFrom);
        return range;
    }

    /**
     * A range may not begin before the raw data does, and for the same reason the ceiling above refuses
     * rather than shortens: the answer would be wrong rather than smaller.
     * <p>
     * Only the interior UTC days of a range are answered from the daily rollup, which is kept indefinitely;
     * the two partial days at its edges are counted live, because a site's day is cut in its own timezone and
     * never on a UTC midnight (ADR 0020, D-160). Once the raw data under an edge has been dropped, that edge
     * counts zero — roughly a day missing from a thirty-day report, silently — and a filtered report, which
     * cannot use the rollup at all (D-103), comes back empty while the summary's pageviews stay where they
     * are, because the hourly rollup behind them is also kept forever. Every number would look plausible.
     * <p>
     * Compared as instants rather than as dates, which is not pedantry: the horizon is a UTC day and the
     * range starts at the site's local midnight. In Istanbul a range beginning on the first retained day
     * starts three hours before the raw data does, and those three hours are exactly the edge that is
     * counted live. A date comparison would accept it.
     * <p>
     * No horizon means no floor. Nothing has been dropped on a database that has never published one, so
     * every day a range can name is still there.
     */
    private static void requireWithinRetention(DateRange range, LocalDate retainedFrom) {
        if (retainedFrom == null) {
            return;
        }
        if (range.start().isBefore(retainedFrom.atStartOfDay(ZoneOffset.UTC).toInstant())) {
            throw new IllegalArgumentException("reports reach back to " + retainedFrom);
        }
    }

    static int breakdownSize(int limit) {
        return Math.clamp(limit, 1, MAX_BREAKDOWN_SIZE);
    }

    /**
     * Reads the repeated {@code filter=DIMENSION:value} parameter. The dimension is the same enum a breakdown
     * groups by, so a row the dashboard showed can be clicked straight into a filter, and the value is split
     * off at the first colon because a path contains colons of its own.
     * <p>
     * An empty value is meaningful: it is the row a breakdown returns for visits where the dimension is unknown.
     */
    static List<Filter> parseFilters(List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        if (filters.size() > MAX_FILTERS) {
            throw new IllegalArgumentException("too many filters");
        }
        return filters.stream().map(StatsRequests::parseOne).toList();
    }

    private static Filter parseOne(String filter) {
        int separator = filter.indexOf(':');
        if (separator < 1) {
            throw new IllegalArgumentException("a filter looks like DIMENSION:value");
        }
        Dimension dimension = Dimension.valueOf(filter.substring(0, separator));
        return new Filter(dimension, filter.substring(separator + 1));
    }

    /** Site Management's vocabulary into this side's. {@code PAGEVIEW} is a page here: there is nothing else. */
    static GoalDefinition toDefinition(ReadableGoal goal) {
        GoalDefinition.Kind kind = "PAGEVIEW".equals(goal.type())
                ? GoalDefinition.Kind.PAGE
                : GoalDefinition.Kind.EVENT;
        return new GoalDefinition(goal.goalId(), kind, goal.target());
    }
}
