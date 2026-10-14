package world.wholestory.api.analytics.application;

/**
 * What a breakdown groups by, and what a filter narrows on.
 * <p>
 * Most of these live on both tables: the geo and device fields are denormalised onto events as well as sessions,
 * which is what lets "top pages, Turkey only" be a plain {@code where} rather than a join. Two are not. A page
 * belongs to a pageview, so it exists only on events; an entry or exit page is a property of the whole visit, so
 * it exists only on sessions. Filtering across that line is the one case that needs a semi-join.
 * <p>
 * Each also says whether the daily visitor rollup holds it. That is not a detail of this enum but a contract
 * with another service: processor's {@code VisitorDailyRollup} computes exactly this set, and a dimension
 * marked here that it does not compute would make a report silently short, since the rollup would contribute
 * no rows and only the two partial days at the edges of the range would be counted. Both sides pin the list
 * in a test for that reason.
 */
public enum Dimension {

    PAGE(Source.EVENTS, "pathname", null, null, "pathname", false),
    ENTRY_PAGE(Source.SESSIONS, "entry_page", null, "entry_page", null, false),
    EXIT_PAGE(Source.SESSIONS, "exit_page", null, "exit_page", null, false),
    SOURCE(Source.SESSIONS, "referrer_source", null, "referrer_source", "referrer_source", true),
    COUNTRY(Source.SESSIONS, "country_code", null, "country_code", "country_code", true),
    REGION(Source.SESSIONS, "subdivision_code", "subdivision_name", "subdivision_code", "subdivision_code", true),
    CITY(Source.SESSIONS, "city_geoname_id", "city_name", "city_geoname_id", "city_geoname_id", true),
    BROWSER(Source.SESSIONS, "browser", null, "browser", "browser", true),
    OS(Source.SESSIONS, "os", null, "os", "os", true),
    DEVICE(Source.SESSIONS, "device_type", null, "device_type", "device_type", true),
    UTM_SOURCE(Source.SESSIONS, "utm_source", null, "utm_source", "utm_source", false),
    UTM_MEDIUM(Source.SESSIONS, "utm_medium", null, "utm_medium", "utm_medium", false),
    UTM_CAMPAIGN(Source.SESSIONS, "utm_campaign", null, "utm_campaign", "utm_campaign", false),
    UTM_CONTENT(Source.SESSIONS, "utm_content", null, "utm_content", "utm_content", false),
    UTM_TERM(Source.SESSIONS, "utm_term", null, "utm_term", "utm_term", false);

    public enum Source {
        SESSIONS,
        EVENTS
    }

    private final Source source;
    private final String keyColumn;
    private final String labelColumn;
    private final String sessionColumn;
    private final String eventColumn;
    private final boolean rolledUp;

    Dimension(Source source, String keyColumn, String labelColumn, String sessionColumn, String eventColumn,
              boolean rolledUp) {
        this.source = source;
        this.keyColumn = keyColumn;
        this.labelColumn = labelColumn;
        this.sessionColumn = sessionColumn;
        this.eventColumn = eventColumn;
        this.rolledUp = rolledUp;
    }

    /**
     * Whether the daily visitor rollup holds this dimension, and so whether a report over it may be answered
     * from there. High cardinality is why the rest are not: a row per entry page or per campaign per day
     * would make the rollup a second copy of the sessions rather than a summary of them.
     */
    public boolean rolledUp() {
        return rolledUp;
    }

    /** Which table a breakdown of this dimension is grouped from. */
    public Source source() {
        return source;
    }

    /** Column names come from here, never from a request, so nothing a caller sends can shape the SQL. */
    public String keyColumn() {
        return keyColumn;
    }

    /**
     * A column holding the human readable name, where the key is not one: a city is a geoname id and a region an
     * ISO code. A country code the client can name itself.
     */
    public String labelColumn() {
        return labelColumn;
    }

    /** The column on {@code api_sessions}, or null when this dimension does not exist there. */
    public String sessionColumn() {
        return sessionColumn;
    }

    /** The column on {@code api_events}, or null when this dimension does not exist there. */
    public String eventColumn() {
        return eventColumn;
    }
}
