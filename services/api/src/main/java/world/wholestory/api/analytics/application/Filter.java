package world.wholestory.api.analytics.application;

/**
 * Narrows every number on the dashboard to the visits that match.
 *
 * @param value the key as a breakdown returned it; empty means the dimension was not known for those visits,
 *              which is a thing a reader can click on just like any other row
 */
public record Filter(Dimension dimension, String value) {

    public Filter {
        if (dimension == null || value == null) {
            throw new IllegalArgumentException("a filter needs a dimension and a value");
        }
    }
}
