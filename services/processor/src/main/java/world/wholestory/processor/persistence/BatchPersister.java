package world.wholestory.processor.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.processor.rollup.RollupWriter;
import world.wholestory.processor.sessionization.SessionizedEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Writes a batch atomically. Sessions and rollups are derived only from events that were actually inserted,
 * so a replayed batch (at-least-once delivery) changes nothing (D-018, D-032).
 */
@Service
@RequiredArgsConstructor
public class BatchPersister {

    private final EventWriter eventWriter;
    private final SessionWriter sessionWriter;
    private final RollupWriter rollupWriter;

    @Transactional
    public int persist(List<SessionizedEvent> events) {
        if (events.isEmpty()) {
            return 0;
        }
        Set<UUID> inserted = eventWriter.insert(events);
        List<SessionizedEvent> fresh = events.stream()
                .filter(e -> inserted.contains(e.event().eventId()))
                .toList();
        if (fresh.isEmpty()) {
            return 0;
        }
        sessionWriter.upsert(fresh);
        rollupWriter.apply(fresh);
        return fresh.size();
    }
}
