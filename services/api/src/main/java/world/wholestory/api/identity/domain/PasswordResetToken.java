package world.wholestory.api.identity.domain;

import lombok.AccessLevel;
import lombok.Getter;
import world.wholestory.api.shared.domain.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A reset link that was handed out, and whether it can still be followed.
 * <p>
 * It is its own aggregate rather than a part of {@link User}: it has a lifetime of its own, several can be
 * outstanding at once, and the rules that matter — spent once, and only before it expires — are about the link
 * and not about the person.
 * <p>
 * The aggregate holds the {@link ResetTokenHash}, never the secret itself.
 */
@Getter
public final class PasswordResetToken {

    /**
     * Long enough to walk to another device and find the mail, short enough that a link left in an inbox stops
     * being a credential by the end of the hour.
     */
    public static final Duration LIFETIME = Duration.ofHours(1);

    private final ResetTokenHash tokenHash;
    private final UserId userId;
    private final Instant expiresAt;
    /** Read through {@link #usedAt()} so callers have to handle the unused case. */
    @Getter(AccessLevel.NONE)
    private Instant usedAt;

    private PasswordResetToken(ResetTokenHash tokenHash, UserId userId, Instant expiresAt, Instant usedAt) {
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.userId = Objects.requireNonNull(userId);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.usedAt = usedAt;
    }

    public static PasswordResetToken issue(ResetTokenHash tokenHash, UserId userId, Instant now) {
        return new PasswordResetToken(tokenHash, userId, now.plus(LIFETIME), null);
    }

    /** Rebuilds a stored token. For persistence adapters only. */
    public static PasswordResetToken restore(ResetTokenHash tokenHash, UserId userId, Instant expiresAt,
                                             Instant usedAt) {
        return new PasswordResetToken(tokenHash, userId, expiresAt, usedAt);
    }

    /**
     * Spends the link. This is the check that makes a reset link single use, which matters because the link
     * travels through mail and may be read again long after it was followed.
     *
     * @throws InvalidResetTokenException when it was already used or has expired
     */
    public void redeem(Instant now) {
        if (!isUsable(now)) {
            throw new InvalidResetTokenException();
        }
        this.usedAt = now;
    }

    /**
     * Retires a link without following it, for the other links outstanding when a password is reset. Idempotent
     * and never refuses: the point is only that it stops working, and an already spent or expired one already has.
     */
    public void invalidate(Instant now) {
        if (usedAt == null) {
            this.usedAt = now;
        }
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public Optional<Instant> usedAt() {
        return Optional.ofNullable(usedAt);
    }
}
