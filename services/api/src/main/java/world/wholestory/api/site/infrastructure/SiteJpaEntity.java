package world.wholestory.api.site.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sites", schema = "sites")
@Getter
@Setter
@NoArgsConstructor
class SiteJpaEntity {

    @Id
    private UUID id;

    /** No foreign key into identity: that is a context boundary, not an oversight (D-038). */
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "public_dashboard", nullable = false)
    private boolean publicDashboard;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Set when the site was removed; the partial unique index frees the domain as soon as it is (D-034). */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
