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

/** Storage shape of a user. Deliberately separate from the aggregate: the table outlives any model change. */
@Entity
@Table(name = "users", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
class UserJpaEntity {

    @Id
    private UUID id;

    /** The column is {@code citext}, so uniqueness ignores capitalisation as well. */
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
