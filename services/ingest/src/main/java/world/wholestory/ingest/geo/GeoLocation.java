package world.wholestory.ingest.geo;

/**
 * Coarse location derived from the client IP, which is discarded immediately afterwards.
 * Coordinates are deliberately not resolved: the product promise is country, region and city, nothing finer.
 */
public record GeoLocation(String countryCode, String subdivisionCode, Integer cityGeonameId) {

    /** Used when the address is private, absent from the database, or the database is unavailable. */
    public static final GeoLocation UNKNOWN = new GeoLocation(null, null, null);
}
