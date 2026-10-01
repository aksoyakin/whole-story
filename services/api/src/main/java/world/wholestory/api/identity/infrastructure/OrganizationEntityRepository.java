package world.wholestory.api.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface OrganizationEntityRepository extends JpaRepository<OrganizationJpaEntity, UUID> {

    @Query("""
            select o from OrganizationJpaEntity o
            join o.memberships m
            where m.userId = :userId
            order by o.createdAt
            """)
    List<OrganizationJpaEntity> findByMember(@Param("userId") UUID userId);
}
