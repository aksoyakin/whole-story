package world.wholestory.api.analytics.application;

/**
 * One row of a breakdown.
 *
 * One number, because one number is what a breakdown row shows. It carried visits and pageviews as well
 * until they were measured: nothing had ever rendered them, and on the page breakdown the visits figure
 * alone — a {@code count(distinct session_id)} over every event in the range — was nine of the twenty
 * seconds that breakdown cost over twelve months. The cheapest work is the work that is not done.
 *
 * @param key      the grouped value, empty when it is not known for those visits
 * @param label    what to print: the place's name where the key is an id or a code, otherwise the key itself.
 *                 Never empty, so the client never has to invent a fallback — a country code is the one it may
 *                 still improve on, since the browser can name a country without any help from here.
 * @param visitors unique visitors, which is also what the rows are ranked by
 */
public record BreakdownEntry(String key, String label, long visitors) {
}
