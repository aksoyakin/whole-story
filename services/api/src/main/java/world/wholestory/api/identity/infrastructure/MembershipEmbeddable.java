package world.wholestory.api.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import world.wholestory.api.identity.domain.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * A membership has no identity outside its organization, so it is mapped as part of the aggregate rather than as
 * an entity of its own. Hibernate rewrites the whole collection when it changes, which is the right trade for a
 * list that holds a handful of rows.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class MembershipEmbeddable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
