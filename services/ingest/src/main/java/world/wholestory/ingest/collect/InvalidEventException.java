package world.wholestory.ingest.collect;

import lombok.Getter;

@Getter
class InvalidEventException extends RuntimeException {

    /** Short, low-cardinality tag for the rejection metric. */
    private final String reason;

    InvalidEventException(String reason, String message) {
        super(message);
        this.reason = reason;
    }
}
