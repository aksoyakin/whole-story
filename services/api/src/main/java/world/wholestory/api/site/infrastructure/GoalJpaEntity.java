package world.wholestory.api.site.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import world.wholestory.api.site.domain.GoalType;

import java.time.Instant;
import java.util.UUID;

/**
 * Storage shape of a goal. The target is two nullable columns here and one value object in the model: the check
 * constraint on the table and {@code GoalTarget} say the same thing in their own language.
 */
@Entity
@Table(name = "goals", schema = "sites")
@Getter
@Setter
@NoArgsConstructor
class GoalJpaEntity {

    @Id
    private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalType type;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "page_path")
    private String pagePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
