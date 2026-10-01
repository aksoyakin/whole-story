package world.wholestory.api.site.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SiteEntityRepository extends JpaRepository<SiteJpaEntity, UUID> {

    List<SiteJpaEntity> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAt(UUID organizationId);

    int countByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
