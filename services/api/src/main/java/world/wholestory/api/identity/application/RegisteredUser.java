package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.OrganizationId;
import world.wholestory.api.identity.domain.UserId;

public record RegisteredUser(UserId userId, OrganizationId organizationId) {
}
