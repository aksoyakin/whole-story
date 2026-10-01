package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.OrganizationAccess;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.shared.domain.UserId;
import world.wholestory.api.site.domain.NotAnOrganizationMemberException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListSites {

    private final SiteRepository sites;
    private final OrganizationAccess organizations;

    @Transactional(readOnly = true)
    public List<SiteSummary> of(UUID organizationId, UUID actingUserId) {
        OrganizationId organization = OrganizationId.of(organizationId);
        if (!organizations.isMember(organization, UserId.of(actingUserId))) {
            throw new NotAnOrganizationMemberException();
        }
        return sites.findByOrganization(organization).stream().map(SiteMapper::toSummary).toList();
    }
}
