package world.wholestory.api.analytics.application;

/**
 * What a breakdown can be grouped by.
 * <p>
 * Most of these are session attributes, so one row of {@code api_sessions} answers visitors, visits and
 * pageviews together. {@link #PAGE} is not: a session visits many pages, so it is grouped from the events
 * themselves.
 */
public enum Dimension {

    /** Grouped from events: a page is a property of a pageview, not of a visit. */
    PAGE(Source.EVENTS, "pathname"),
    ENTRY_PAGE(Source.SESSIONS, "entry_page"),
    EXIT_PAGE(Source.SESSIONS, "exit_page"),
    SOURCE(Source.SESSIONS, "referrer_source"),
    COUNTRY(Source.SESSIONS, "country_code"),
    REGION(Source.SESSIONS, "subdivision_code", "subdivision_name"),
    CITY(Source.SESSIONS, "city_geoname_id", "city_name"),
    BROWSER(Source.SESSIONS, "browser"),
    OS(Source.SESSIONS, "os"),
    DEVICE(Source.SESSIONS, "device_type"),
    UTM_SOURCE(Source.SESSIONS, "utm_source"),
    UTM_MEDIUM(Source.SESSIONS, "utm_medium"),
    UTM_CAMPAIGN(Source.SESSIONS, "utm_campaign"),
    UTM_CONTENT(Source.SESSIONS, "utm_content"),
    UTM_TERM(Source.SESSIONS, "utm_term");

    public enum Source {
        SESSIONS,
        EVENTS
    }

    private final Source source;
    private final String keyColumn;
    private final String labelColumn;

    Dimension(Source source, String keyColumn) {
        this(source, keyColumn, null);
    }

    Dimension(Source source, String keyColumn, String labelColumn) {
        this.source = source;
        this.keyColumn = keyColumn;
        this.labelColumn = labelColumn;
    }

    public Source source() {
        return source;
    }

    /** Column names are chosen here, never taken from a request, so no request can shape the SQL. */
    public String keyColumn() {
        return keyColumn;
    }

    /**
     * A column holding the human readable name, where the key is not readable on its own: a city is a geoname
     * id and a region an ISO code. A country code the client can name itself.
     */
    public String labelColumn() {
        return labelColumn;
    }
}
