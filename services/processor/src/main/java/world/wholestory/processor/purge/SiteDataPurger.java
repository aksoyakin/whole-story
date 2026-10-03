package world.wholestory.processor.purge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Deletes everything the analytics schema holds for one site (D-034).
 * <p>
 * It runs here rather than in api because the schema belongs to the processor (D-012): api may only read the
 * {@code api_*} views and has no privilege to delete a row. api knows a site was removed, the processor is the
 * only one that can act on it, which is why the two are joined by a message rather than a method call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteDataPurger {

    /**
     * Deleted a chunk at a time, each chunk its own transaction. A busy site is millions of rows, and removing
     * them in one statement would hold locks and pile up WAL for minutes — long enough for the consumer to miss
     * its poll deadline, be dropped from the group and start the whole thing again on rebalance, for ever.
     */
    private static final int CHUNK = 5_000;

    /**
     * Rows are picked by primary key rather than by {@code ctid}. Events and sessions are partitioned, and a
     * ctid is only unique inside one partition — matching on it across the parent could delete a row it was
     * never shown. Table and key names are constants here and never come from a message, so nothing on the wire
     * can shape the statement (the discipline D-098 applies to the reporting side).
     */
    private static final List<Target> TARGETS = List.of(
            new Target("analytics.events", "event_id, timestamp"),
            new Target("analytics.sessions", "session_id, started_at"),
            new Target("analytics.page_hourly", "site_id, hour, pathname"),
            new Target("analytics.custom_event_hourly", "site_id, hour, event_name"));

    private static final String DELETE_CHUNK = """
            delete from %1$s where (%2$s) in (
                select %2$s from %1$s where site_id = ? limit %3$d)
            """;

    private final JdbcClient jdbc;

    /**
     * Idempotent: a site that was already purged simply deletes nothing, which is what lets the message be
     * delivered more than once (ADR 0006) and what makes replaying the topic harmless.
     *
     * @return how many rows went, across every table
     */
    public long purge(UUID siteId) {
        long total = 0;
        for (Target target : TARGETS) {
            total += purgeFrom(target, siteId);
        }
        log.info("Purged {} rows for site {}", total, siteId);
        return total;
    }

    private long purgeFrom(Target target, UUID siteId) {
        String sql = DELETE_CHUNK.formatted(target.table(), target.keyColumns(), CHUNK);
        long removed = 0;
        int inChunk;
        do {
            inChunk = jdbc.sql(sql).param(siteId).update();
            removed += inChunk;
            // A short chunk means the table had nothing left to give.
        } while (inChunk == CHUNK);
        return removed;
    }

    private record Target(String table, String keyColumns) {
    }
}
