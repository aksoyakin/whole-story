package world.wholestory.api.site.infrastructure;

/**
 * What a public dashboard may say about the site it belongs to: its name, and where its day starts.
 * <p>
 * The site id is deliberately absent. Everything a shared dashboard asks for is addressed by domain, so there
 * is nothing to gain from publishing an internal identifier to whoever opens the page.
 */
record SharedSiteResponse(String domain, String timezone) {
}
