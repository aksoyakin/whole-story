package world.wholestory.api.site.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.site.application.SiteRepository;
import world.wholestory.api.site.domain.Domain;
import world.wholestory.api.site.domain.DomainAlreadyTrackedException;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JpaSiteRepository implements SiteRepository {

    /** The partial unique index on sites.sites(domain) where deleted_at is null. */
    private static final String DOMAIN_UNIQUE_INDEX = "sites_domain_active_uq";

    private final SiteEntityRepository sites;

    @Override
    public void save(Site site) {
        try {
            sites.saveAndFlush(SiteJpaMapper.toEntity(site));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateDomain(e)) {
                throw new DomainAlreadyTrackedException(site.getDomain());
            }
            throw e;
        }
    }

    @Override
    public Optional<Site> findById(SiteId id) {
        return sites.findById(id.value()).map(SiteJpaMapper::toDomain);
    }

    /** The value object has already normalised the name, so this compares what the index stores. */
    @Override
    public Optional<Site> findByDomain(Domain domain) {
        return sites.findByDomainAndDeletedAtIsNull(domain.value()).map(SiteJpaMapper::toDomain);
    }

    @Override
    public List<Site> findByOrganization(OrganizationId organizationId) {
        return sites.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAt(organizationId.value()).stream()
                .map(SiteJpaMapper::toDomain)
                .toList();
    }

    @Override
    public int countByOrganization(OrganizationId organizationId) {
        return sites.countByOrganizationIdAndDeletedAtIsNull(organizationId.value());
    }

    private static boolean isDuplicateDomain(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(DOMAIN_UNIQUE_INDEX);
    }
}
