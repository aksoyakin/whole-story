package world.wholestory.api.identity.infrastructure;

import world.wholestory.api.identity.domain.PasswordResetToken;
import world.wholestory.api.identity.domain.ResetTokenHash;
import world.wholestory.api.shared.domain.UserId;

final class PasswordResetTokenJpaMapper {

    private PasswordResetTokenJpaMapper() {
    }

    static PasswordResetTokenJpaEntity toEntity(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
        entity.setTokenHash(token.getTokenHash().value());
        entity.setUserId(token.getUserId().value());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setUsedAt(token.usedAt().orElse(null));
        return entity;
    }

    static PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        return PasswordResetToken.restore(
                ResetTokenHash.of(entity.getTokenHash()),
                UserId.of(entity.getUserId()),
                entity.getExpiresAt(),
                entity.getUsedAt());
    }
}
