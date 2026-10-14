package world.wholestory.processor.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.processor.rollup.RollupWriter;
import world.wholestory.processor.rollup.VisitorDailyDirtyDays;
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
    private final VisitorDailyDirtyDays dirtyDays;

    /**
     * @return the events this call actually stored, in arrival order; empty for a batch that had all been seen
     *         before. Returned rather than counted because everything derived from a batch has to be derived
     *         from these and not from what arrived: the caller has its own derivation to do.
     */
    @Transactional
    public List<SessionizedEvent> persist(List<SessionizedEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        Set<UUID> inserted = eventWriter.insert(events);
        List<SessionizedEvent> fresh = events.stream()
                .filter(e -> inserted.contains(e.event().eventId()))
                .toList();
        if (fresh.isEmpty()) {
            return List.of();
        }
        sessionWriter.upsert(fresh);
        rollupWriter.apply(fresh);
        // Last, and inside this transaction rather than after it. The hourly rollups above are additive, so a
        // late batch simply adds to them; the daily one counts each day once and cannot, so a batch landing
        // for a day already counted has to leave a note asking for that day to be counted again (Q-21). A
        // note that could be lost while these rows were kept would leave the rollup permanently short while
        // claiming to be complete, which is the failure it exists to prevent.
        dirtyDays.mark(fresh);
        return fresh;
    }
}
