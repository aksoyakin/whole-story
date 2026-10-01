package world.wholestory.api.identity.infrastructure;

import java.util.UUID;

record UserProfileResponse(UUID userId, String email, String name, UUID organizationId, String role) {
}
