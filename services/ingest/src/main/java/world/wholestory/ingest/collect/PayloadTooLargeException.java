package world.wholestory.ingest.collect;

/**
 * One event arrived larger than ingest accepts.
 * <p>
 * A type of its own rather than another {@link InvalidEventException} reason, because this is the one rejection
 * that cannot wait for validation: it is raised while the body is still being read, before there is an event to
 * find anything wrong with.
 */
class PayloadTooLargeException extends RuntimeException {

    PayloadTooLargeException() {
        super("event payload is too large");
    }
}
