package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import world.wholestory.api.identity.application.PasswordResetTokenRepository;
import world.wholestory.api.identity.domain.PasswordResetToken;
import world.wholestory.api.identity.domain.ResetTokenHash;
import world.wholestory.api.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final PasswordResetTokenEntityRepository tokens;

    @Override
    public void save(PasswordResetToken token) {
        tokens.save(PasswordResetTokenJpaMapper.toEntity(token));
    }

    @Override
    public Optional<PasswordResetToken> findByHash(ResetTokenHash hash) {
        return tokens.findById(hash.value()).map(PasswordResetTokenJpaMapper::toDomain);
    }

    @Override
    public List<PasswordResetToken> findUnusedFor(UserId userId) {
        return tokens.findByUserIdAndUsedAtIsNull(userId.value()).stream()
                .map(PasswordResetTokenJpaMapper::toDomain)
                .toList();
    }
}
