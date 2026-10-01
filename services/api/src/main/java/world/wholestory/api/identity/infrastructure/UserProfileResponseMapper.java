package world.wholestory.api.identity.infrastructure;

import world.wholestory.api.identity.application.UserProfile;

final class UserProfileResponseMapper {

    private UserProfileResponseMapper() {
    }

    static UserProfileResponse toResponse(UserProfile profile) {
        return new UserProfileResponse(
                profile.userId().value(),
                profile.email().value(),
                profile.name().value(),
                profile.organizationId().value(),
                profile.role().name());
    }
}
