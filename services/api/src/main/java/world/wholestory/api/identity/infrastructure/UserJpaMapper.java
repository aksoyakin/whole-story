package world.wholestory.api.identity.infrastructure;

import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.PasswordHash;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.User;
import world.wholestory.api.shared.domain.UserId;

/** Hand-written on purpose: no mapping library in this project, and the two shapes may drift apart. */
final class UserJpaMapper {

    private UserJpaMapper() {
    }

    static UserJpaEntity toEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(user.getId().value());
        entity.setEmail(user.getEmail().value());
        entity.setName(user.getName().value());
        entity.setPasswordHash(user.getPasswordHash().value());
        entity.setEmailVerifiedAt(user.emailVerifiedAt().orElse(null));
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }

    static User toDomain(UserJpaEntity entity) {
        return User.restore(
                UserId.of(entity.getId()),
                EmailAddress.of(entity.getEmail()),
                PersonName.of(entity.getName()),
                PasswordHash.of(entity.getPasswordHash()),
                entity.getEmailVerifiedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
