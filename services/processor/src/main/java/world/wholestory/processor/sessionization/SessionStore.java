package world.wholestory.processor.sessionization;

import java.util.Collection;
import java.util.Map;

interface SessionStore {

    Map<String, SessionState> load(Collection<String> keys);

    void save(Map<String, SessionState> sessions);
}
