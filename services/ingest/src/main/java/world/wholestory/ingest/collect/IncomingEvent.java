package world.wholestory.ingest.collect;

import java.util.Map;

/** Payload sent by the tracker. */
record IncomingEvent(String name, String url, String domain, String referrer, Map<String, String> props) {
}
