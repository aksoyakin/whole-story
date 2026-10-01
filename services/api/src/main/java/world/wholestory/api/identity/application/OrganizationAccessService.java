package world.wholestory.api.identity.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.OrganizationAccess;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.shared.domain.UserId;

/** Answers the published questions of this context by asking the aggregate that holds the rules. */
@Service
@RequiredArgsConstructor
class OrganizationAccessService implements OrganizationAccess {

    private final OrganizationRepository organizations;

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(OrganizationId organizationId, UserId userId) {
        return organizations.findById(organizationId).filter(o -> o.hasMember(userId)).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean allowsAnotherSite(OrganizationId organizationId, int currentSiteCount) {
        return organizations.findById(organizationId)
                .filter(organization -> organization.allowsAnotherSite(currentSiteCount))
                .isPresent();
    }
}
