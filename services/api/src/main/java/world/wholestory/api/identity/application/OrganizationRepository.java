package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.Organization;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {

    void save(Organization organization);

    Optional<Organization> findById(OrganizationId id);

    /** Every organization the user is a member of. In v1 that is always exactly one. */
    List<Organization> findByMember(UserId userId);
}
