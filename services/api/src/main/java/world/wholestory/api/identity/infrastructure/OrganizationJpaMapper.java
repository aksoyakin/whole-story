package world.wholestory.api.identity.infrastructure;

import world.wholestory.api.identity.domain.Membership;
import world.wholestory.api.identity.domain.Organization;
import world.wholestory.api.identity.domain.OrganizationId;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.UserId;

import java.util.List;

final class OrganizationJpaMapper {

    private OrganizationJpaMapper() {
    }

    static OrganizationJpaEntity toEntity(Organization organization) {
        OrganizationJpaEntity entity = new OrganizationJpaEntity();
        entity.setId(organization.getId().value());
        entity.setName(organization.getName().value());
        entity.setSiteLimit(organization.getSiteLimit());
        entity.setMonthlyEventLimit(organization.getMonthlyEventLimit());
        entity.setMemberships(organization.memberships().stream()
                .map(membership -> new MembershipEmbeddable(
                        membership.userId().value(), membership.role(), membership.createdAt()))
                .toList());
        entity.setCreatedAt(organization.getCreatedAt());
        entity.setUpdatedAt(organization.getUpdatedAt());
        return entity;
    }

    static Organization toDomain(OrganizationJpaEntity entity) {
        List<Membership> memberships = entity.getMemberships().stream()
                .map(stored -> new Membership(
                        UserId.of(stored.getUserId()), stored.getRole(), stored.getCreatedAt()))
                .toList();
        return Organization.restore(
                OrganizationId.of(entity.getId()),
                PersonName.of(entity.getName()),
                entity.getSiteLimit(),
                entity.getMonthlyEventLimit(),
                memberships,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
