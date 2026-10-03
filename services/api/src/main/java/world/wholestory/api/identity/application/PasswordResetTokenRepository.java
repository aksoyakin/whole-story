package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.PasswordResetToken;
import world.wholestory.api.identity.domain.ResetTokenHash;
import world.wholestory.api.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port to the reset links that were handed out. Lookup is by hash, because the secret is never stored. */
public interface PasswordResetTokenRepository {

    void save(PasswordResetToken token);

    Optional<PasswordResetToken> findByHash(ResetTokenHash hash);

    /** The links still outstanding for someone, so that resetting a password can retire the rest. */
    List<PasswordResetToken> findUnusedFor(UserId userId);

    /**
     * Forgets the links that can no longer be followed. Expiry is the only condition needed: a spent link is
     * refused by the aggregate and expires within the hour anyway, so every row reaches this eventually.
     *
     * @return how many rows went, for the log
     */
    int deleteExpired(Instant cutoff);
}
