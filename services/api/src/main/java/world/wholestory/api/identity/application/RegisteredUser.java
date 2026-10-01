package world.wholestory.api.identity.application;

import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.shared.domain.UserId;

public record RegisteredUser(UserId userId, OrganizationId organizationId) {
}
