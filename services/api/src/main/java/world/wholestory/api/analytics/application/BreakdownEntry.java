package world.wholestory.api.analytics.application;

/**
 * One row of a breakdown.
 *
 * @param key   the grouped value, empty when it is not known for those visits
 * @param label what to print: the place's name where the key is an id or a code, otherwise the key itself.
 *              Never empty, so the client never has to invent a fallback — a country code is the one it may
 *              still improve on, since the browser can name a country without any help from here.
 */
public record BreakdownEntry(String key, String label, long visitors, long visits, long pageviews) {
}
