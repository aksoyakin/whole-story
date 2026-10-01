package world.wholestory.api.identity.infrastructure;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "organizations", schema = "identity")
@Getter
@Setter
@NoArgsConstructor
class OrganizationJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "site_limit", nullable = false)
    private int siteLimit;

    @Column(name = "monthly_event_limit", nullable = false)
    private long monthlyEventLimit;

    /**
     * Eager on purpose: an aggregate is always loaded whole, and with {@code open-in-view} off a lazy collection
     * would fail the moment the transaction ends.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "memberships", schema = "identity",
            joinColumns = @JoinColumn(name = "organization_id", nullable = false))
    private List<MembershipEmbeddable> memberships = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
