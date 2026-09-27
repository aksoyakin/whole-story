package world.wholestory.processor.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.StringJoiner;

/** Helpers for multi-row {@code INSERT ... VALUES (...), (...)} statements with positional parameters. */
public final class MultiRowInsert {

    private MultiRowInsert() {
    }

    /** {@code rowTemplate} is one row, e.g. {@code "(?, ?, ?::jsonb)"}. */
    public static String values(String rowTemplate, int rows) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < rows; i++) {
            joiner.add(rowTemplate);
        }
        return joiner.toString();
    }

    public static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
