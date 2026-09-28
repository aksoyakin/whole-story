package world.wholestory.ingest.collect;

class RateLimitExceededException extends RuntimeException {

    RateLimitExceededException() {
        super("too many events for this visitor");
    }
}
