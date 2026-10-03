package world.wholestory.api.identity.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Forgets reset links once they can no longer be followed.
 * <p>
 * Without this the table only ever grows: a row is written for every link ever asked for, and nothing has ever
 * removed one — neither following a link nor expiring deletes it, because both only need the row to stay and
 * say no. Keeping a hash of a dead credential forever is nothing but a liability, and the rate limit means the
 * rows accumulate as fast as anyone cares to ask.
 * <p>
 * Expiry is the whole condition. A spent link is refused by the aggregate and dies within the hour anyway
 * (ADR 0021), so every row reaches this eventually and there is no second rule to keep in step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeExpiredResetTokens {

    private final PasswordResetTokenRepository tokens;
    private final Clock clock;

    /** Nightly: nothing here is urgent, and an expired link has already stopped working. */
    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void purge() {
        int deleted = tokens.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Deleted {} expired password reset tokens", deleted);
        }
    }
}
