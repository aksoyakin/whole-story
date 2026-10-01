package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.UserId;

import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A person who can sign in. The aggregate owns the credential and nothing else: which organizations the user
 * belongs to is the {@link Organization} aggregate's business, so the two reference each other by id only.
 */
@Getter
public final class User {

    private final UserId id;
    private final EmailAddress email;
    private PersonName name;
    private PasswordHash passwordHash;
    /** Read through {@link #emailVerifiedAt()} so that callers have to handle the unconfirmed case. */
    @Getter(AccessLevel.NONE)
    private Instant emailVerifiedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, EmailAddress email, PersonName name, PasswordHash passwordHash,
                 Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.name = Objects.requireNonNull(name);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * A new account. The address is not verified yet: v1 does not send a confirmation mail, so
     * {@code emailVerifiedAt} stays empty until that feature exists.
     */
    public static User register(UserId id, EmailAddress email, PersonName name, PasswordHash passwordHash,
                                Instant now) {
        return new User(id, email, name, passwordHash, null, now, now);
    }

    /** Rebuilds a stored user. For persistence adapters only: invariants were checked when it was registered. */
    public static User restore(UserId id, EmailAddress email, PersonName name, PasswordHash passwordHash,
                               Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {
        return new User(id, email, name, passwordHash, emailVerifiedAt, createdAt, updatedAt);
    }

    public void changePassword(PasswordHash newHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newHash);
        this.updatedAt = now;
    }

    public void rename(PersonName newName, Instant now) {
        this.name = Objects.requireNonNull(newName);
        this.updatedAt = now;
    }

    /** Idempotent: confirming an already confirmed address keeps the original time. */
    public void confirmEmail(Instant now) {
        if (emailVerifiedAt == null) {
            this.emailVerifiedAt = now;
            this.updatedAt = now;
        }
    }

    public Optional<Instant> emailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}
