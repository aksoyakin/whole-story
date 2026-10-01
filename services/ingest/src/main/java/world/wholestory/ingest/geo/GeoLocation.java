package world.wholestory.ingest.geo;

/**
 * Coarse location derived from the client IP, which is discarded immediately afterwards.
 * Coordinates are deliberately not resolved: the product promise is country, region and city, nothing finer.
 * <p>
 * Codes and names travel together. The code is what a map and a filter use, while the name is what a person
 * reads; resolving the name here costs nothing, because it comes out of the same database lookup, and it saves
 * the reporting side from needing a second copy of MaxMind's data just to label a row.
 */
public record GeoLocation(
        String countryCode,
        String subdivisionCode,
        String subdivisionName,
        Integer cityGeonameId,
        String cityName) {

    /** Used when the address is private, absent from the database, or the database is unavailable. */
    public static final GeoLocation UNKNOWN = new GeoLocation(null, null, null, null, null);
}
