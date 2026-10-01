package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import world.wholestory.api.identity.application.OrganizationRepository;
import world.wholestory.api.identity.domain.Organization;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JpaOrganizationRepository implements OrganizationRepository {

    private final OrganizationEntityRepository organizations;

    @Override
    public void save(Organization organization) {
        organizations.save(OrganizationJpaMapper.toEntity(organization));
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return organizations.findById(id.value()).map(OrganizationJpaMapper::toDomain);
    }

    @Override
    public List<Organization> findByMember(UserId userId) {
        return organizations.findByMember(userId.value()).stream().map(OrganizationJpaMapper::toDomain).toList();
    }
}
