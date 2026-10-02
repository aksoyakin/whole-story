package world.wholestory.api.site.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface GoalEntityRepository extends JpaRepository<GoalJpaEntity, UUID> {

    List<GoalJpaEntity> findBySiteIdOrderByCreatedAt(UUID siteId);
}
