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

    /**
     * Sites whose collected data may be deleted, published by Site Management and consumed by the processor.
     * Key: the site id.
     * <p>
     * Separate from {@link #SITE_EVENTS} because the two carry different kinds of thing. That topic is the
     * current state of a domain and is keyed on it; this one is work that has to happen once, so it is keyed on
     * a site id, which is never reused and therefore cannot be compacted away by anything but itself.
     */
    public static final String SITE_PURGE = "site-purge";

    private Topics() {
    }
}
