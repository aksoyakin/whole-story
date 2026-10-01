package world.wholestory.contracts;

public final class Topics {

    /** Privacy-processed tracker events. Key: {@code siteId:visitorHash}. */
    public static final String RAW_EVENTS = "raw-events";

    /**
     * Which domains are tracked, published by Site Management through the outbox. Key: the domain.
     * <p>
     * Log compacted, so the topic is the full current state and not just the changes: an ingest instance with an
     * empty cache reads it from the beginning and ends up with every tracked domain. Each instance therefore
     * consumes all partitions in a group of its own — this is state every instance needs, not work to share.
     */
    public static final String SITE_EVENTS = "site-events";

    private Topics() {
    }
}
