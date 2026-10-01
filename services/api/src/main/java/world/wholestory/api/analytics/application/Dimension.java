package world.wholestory.api.analytics.application;

/**
 * What a breakdown groups by, and what a filter narrows on.
 * <p>
 * Most of these live on both tables: the geo and device fields are denormalised onto events as well as sessions,
 * which is what lets "top pages, Turkey only" be a plain {@code where} rather than a join. Two are not. A page
 * belongs to a pageview, so it exists only on events; an entry or exit page is a property of the whole visit, so
 * it exists only on sessions. Filtering across that line is the one case that needs a semi-join.
 */
public enum Dimension {

    PAGE(Source.EVENTS, "pathname", null, null, "pathname"),
    ENTRY_PAGE(Source.SESSIONS, "entry_page", null, "entry_page", null),
    EXIT_PAGE(Source.SESSIONS, "exit_page", null, "exit_page", null),
    SOURCE(Source.SESSIONS, "referrer_source", null, "referrer_source", "referrer_source"),
    COUNTRY(Source.SESSIONS, "country_code", null, "country_code", "country_code"),
    REGION(Source.SESSIONS, "subdivision_code", "subdivision_name", "subdivision_code", "subdivision_code"),
    CITY(Source.SESSIONS, "city_geoname_id", "city_name", "city_geoname_id", "city_geoname_id"),
    BROWSER(Source.SESSIONS, "browser", null, "browser", "browser"),
    OS(Source.SESSIONS, "os", null, "os", "os"),
    DEVICE(Source.SESSIONS, "device_type", null, "device_type", "device_type"),
    UTM_SOURCE(Source.SESSIONS, "utm_source", null, "utm_source", "utm_source"),
    UTM_MEDIUM(Source.SESSIONS, "utm_medium", null, "utm_medium", "utm_medium"),
    UTM_CAMPAIGN(Source.SESSIONS, "utm_campaign", null, "utm_campaign", "utm_campaign"),
    UTM_CONTENT(Source.SESSIONS, "utm_content", null, "utm_content", "utm_content"),
    UTM_TERM(Source.SESSIONS, "utm_term", null, "utm_term", "utm_term");

    public enum Source {
        SESSIONS,
        EVENTS
    }

    private final Source source;
    private final String keyColumn;
    private final String labelColumn;
    private final String sessionColumn;
    private final String eventColumn;

    Dimension(Source source, String keyColumn, String labelColumn, String sessionColumn, String eventColumn) {
        this.source = source;
        this.keyColumn = keyColumn;
        this.labelColumn = labelColumn;
        this.sessionColumn = sessionColumn;
        this.eventColumn = eventColumn;
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
