package world.wholestory.api.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface PasswordResetTokenEntityRepository extends JpaRepository<PasswordResetTokenJpaEntity, String> {

    List<PasswordResetTokenJpaEntity> findByUserIdAndUsedAtIsNull(UUID userId);

    int deleteByExpiresAtBefore(Instant cutoff);
}
