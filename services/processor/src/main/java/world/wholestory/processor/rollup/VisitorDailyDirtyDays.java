package world.wholestory.processor.rollup;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import world.wholestory.processor.sessionization.SessionizedEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static world.wholestory.processor.persistence.MultiRowInsert.values;

/**
 * Says that a day's rollup is out of date, so {@link VisitorDailyRollup} can count it again (Q-21, D-164).
 * <p>
 * Called from inside the batch's own transaction, which is the whole point: a mark that could be lost while
 * the rows it describes were kept would be worse than no mark at all, because the rollup would then be
 * permanently short and would say it was complete.
 */
@Component
@RequiredArgsConstructor
public class VisitorDailyDirtyDays {

    /** A VALUES list carries no column type of its own, so the parameter is cast. */
    private static final String DAY = "(?::date)";

    /**
     * Only days the watermark already claims. A batch normally belongs to today, which is always beyond the
     * watermark, so in healthy operation this statement inserts nothing at all and the table stays empty.
     * <p>
     * The comparison is in SQL rather than read into Java first, so that the check and the write are one
     * statement and one round trip. A null watermark -- nothing computed yet -- makes the predicate null and
     * inserts nothing, which is right: a day that was never counted cannot be stale, and the job will reach
     * it on its way forward.
     */
    private static final String MARK = """
            insert into analytics.visitor_daily_dirty (utc_day, marked)
            select d, nextval('analytics.visitor_daily_dirty_seq')
            from (values %s) as days(d)
            where d <= (select covered_through from analytics.visitor_daily_watermark)
            on conflict (utc_day) do update set marked = nextval('analytics.visitor_daily_dirty_seq')
            """;

    private final JdbcClient jdbc;

    /** {@code events} must be the events the batch actually stored, for the reason every other derivation is (D-032). */
    public void mark(List<SessionizedEvent> events) {
        Set<LocalDate> days = daysOf(events);
        if (days.isEmpty()) {
            return;
        }
        jdbc.sql(MARK.formatted(values(DAY, days.size())))
                .params(new ArrayList<Object>(days))
                .update();
    }

    /**
     * Both grains a day is read at, because the rollup is computed by two statements over two tables. The
     * session-backed dimensions are grouped by {@code sessions.started_at} and the page by
     * {@code events.timestamp}, and those are not always the same day: an event at 00:02 belongs to a visit
     * that began at 23:58 the day before. Marking only one of them would leave the other's rows stale on one
     * side of every midnight an outage spanned.
     */
    private static Set<LocalDate> daysOf(List<SessionizedEvent> events) {
        Set<LocalDate> days = new LinkedHashSet<>();
        for (SessionizedEvent event : events) {
            days.add(utcDay(event.sessionStartedAt()));
            days.add(utcDay(event.event().timestamp()));
        }
        return days;
    }

    private static LocalDate utcDay(Instant at) {
        return at.atOffset(ZoneOffset.UTC).toLocalDate();
    }
}
