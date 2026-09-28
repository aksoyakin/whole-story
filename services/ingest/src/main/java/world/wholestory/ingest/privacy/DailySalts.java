package world.wholestory.ingest.privacy;

import java.time.LocalDate;
import java.util.Optional;

/** The daily secrets that make visitor hashes unlinkable across days. */
public interface DailySalts {

    /** The salt of the given UTC day, created on first use. */
    byte[] saltFor(LocalDate utcDate);

    /**
     * The salt of a day that has already passed, looked up but never created: a day without traffic must not
     * get a salt retroactively.
     */
    Optional<byte[]> existingSaltFor(LocalDate utcDate);
}
