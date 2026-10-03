package world.wholestory.api.site.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SiteEntityRepository extends JpaRepository<SiteJpaEntity, UUID> {

    List<SiteJpaEntity> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAt(UUID organizationId);

    Optional<SiteJpaEntity> findByDomainAndDeletedAtIsNull(String domain);

    int countByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
