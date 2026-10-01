package world.wholestory.api.site.infrastructure;

import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.site.domain.Domain;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;
import world.wholestory.api.site.domain.Timezone;

final class SiteJpaMapper {

    private SiteJpaMapper() {
    }

    static SiteJpaEntity toEntity(Site site) {
        SiteJpaEntity entity = new SiteJpaEntity();
        entity.setId(site.getId().value());
        entity.setOrganizationId(site.getOrganizationId().value());
        entity.setDomain(site.getDomain().value());
        entity.setTimezone(site.getTimezone().value());
        entity.setPublicDashboard(site.isPublicDashboard());
        entity.setCreatedAt(site.getCreatedAt());
        entity.setUpdatedAt(site.getUpdatedAt());
        entity.setDeletedAt(site.deletedAt().orElse(null));
        return entity;
    }

    static Site toDomain(SiteJpaEntity entity) {
        return Site.restore(
                SiteId.of(entity.getId()),
                OrganizationId.of(entity.getOrganizationId()),
                Domain.of(entity.getDomain()),
                Timezone.of(entity.getTimezone()),
                entity.isPublicDashboard(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt());
    }
}
