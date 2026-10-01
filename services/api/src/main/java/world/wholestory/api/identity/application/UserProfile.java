package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.Role;
import world.wholestory.api.shared.domain.UserId;

/** What the dashboard needs to know about whoever is signed in. */
public record UserProfile(UserId userId, EmailAddress email, PersonName name,
                          OrganizationId organizationId, Role role) {
}
