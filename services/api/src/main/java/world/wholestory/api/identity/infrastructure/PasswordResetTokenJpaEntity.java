package world.wholestory.api.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Storage shape of a reset link. The hash is the key: the token it came from was never written down. */
@Entity
@Table(name = "password_reset_tokens", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
class PasswordResetTokenJpaEntity {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
