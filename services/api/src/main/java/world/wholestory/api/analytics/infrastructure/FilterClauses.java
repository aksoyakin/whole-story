package world.wholestory.api.analytics.infrastructure;

import world.wholestory.api.analytics.application.Filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a list of filters into the SQL that narrows a query, once, for whichever table is being read.
 * <p>
 * Every comparison is written as {@code coalesce(column::text, '') = :fN}, which gives one shape for three cases:
 * a plain value, a numeric key like a city's geoname id, and the empty key a breakdown uses for "not known".
 * The predicate cannot use an index, which costs nothing here: these narrow a range already selected by
 * {@code (site_id, time)}.
 * <p>
 * Column names come from the dimension enum and parameter names are generated, so nothing a caller sends ever
 * becomes part of the statement.
 */
final class FilterClauses {

    private static final FilterClauses NONE = new FilterClauses("", "", Map.of(), false);

    private final String onSessions;
    private final String onEvents;
    private final Map<String, Object> parameters;
    private final boolean any;

    private FilterClauses(String onSessions, String onEvents, Map<String, Object> parameters, boolean any) {
        this.onSessions = onSessions;
        this.onEvents = onEvents;
        this.parameters = parameters;
        this.any = any;
    }

    static FilterClauses of(List<Filter> filters) {
        if (filters.isEmpty()) {
            return NONE;
        }
        List<String> sessionClauses = new ArrayList<>();
        List<String> eventClauses = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();

        for (int i = 0; i < filters.size(); i++) {
            Filter filter = filters.get(i);
            String name = "f" + i;
            parameters.put(name, filter.value());

            String sessionColumn = filter.dimension().sessionColumn();
            sessionClauses.add(sessionColumn != null
                    ? equals("s." + sessionColumn, name)
                    // Only a page is missing from sessions, so the visit is matched through its events.
                    : """
                      exists (select 1 from analytics.api_events fe
                              where fe.session_id = s.session_id and fe.site_id = s.site_id
                                and fe.timestamp >= :start and fe.timestamp < :end
                                and %s)""".formatted(equals("fe." + filter.dimension().eventColumn(), name)));

            String eventColumn = filter.dimension().eventColumn();
            eventClauses.add(eventColumn != null
                    ? equals("e." + eventColumn, name)
                    // An entry or exit page belongs to the visit, so the event is matched through its session.
                    : """
                      exists (select 1 from analytics.api_sessions fs
                              where fs.session_id = e.session_id and fs.site_id = e.site_id
                                and fs.started_at >= :start and fs.started_at < :end
                                and %s)""".formatted(equals("fs." + filter.dimension().sessionColumn(), name)));
        }
        return new FilterClauses(join(sessionClauses), join(eventClauses), parameters, true);
    }

    /** True when anything is filtered at all, which is what decides whether the rollup can still answer. */
    boolean any() {
        return any;
    }

    String onSessions() {
        return onSessions;
    }

    String onEvents() {
        return onEvents;
    }

    Map<String, Object> parameters() {
        return parameters;
    }

    private static String equals(String column, String parameter) {
        return "coalesce(%s::text, '') = :%s".formatted(column, parameter);
    }

    private static String join(List<String> clauses) {
        return clauses.isEmpty() ? "" : " and " + String.join(" and ", clauses);
    }
}
