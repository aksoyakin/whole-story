package world.wholestory.api.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import world.wholestory.api.TestcontainersConfiguration;
import world.wholestory.api.identity.application.PurgeExpiredResetTokens;
import world.wholestory.contracts.UuidV7;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nothing ever removed a reset token: following a link and letting it expire both only need the row to stay
 * and say no, so the table grew for every link ever asked for. A hash of a credential that stopped working an
 * hour ago is pure liability, and the hourly cap means the rows pile up as fast as anyone cares to ask.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResetTokenPurgeIntegrationTest {

    @Autowired
    PurgeExpiredResetTokens purgeExpiredResetTokens;
    @Autowired
    JdbcClient jdbc;

    @Test
    void forgetsTheLinksThatCanNoLongerBeFollowedAndKeepsTheRest() {
        UUID userId = registerUser();
        Instant now = Instant.now();
        String expired = token(userId, now.minus(Duration.ofHours(2)), null);
        String expiredAndSpent = token(userId, now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(3)));
        String live = token(userId, now.plus(Duration.ofMinutes(30)), null);
        // Spent but not yet expired: one rule is enough, because this one is an hour from being swept too.
        String spent = token(userId, now.plus(Duration.ofMinutes(30)), now);

        purgeExpiredResetTokens.purge();

        assertThat(exists(expired)).isFalse();
        assertThat(exists(expiredAndSpent)).isFalse();
        assertThat(exists(live)).isTrue();
        assertThat(exists(spent)).isTrue();
    }

    @Test
    void runningItOnAnEmptyTableIsHarmless() {
        jdbc.sql("delete from identity.password_reset_tokens").update();

        purgeExpiredResetTokens.purge();

        assertThat(jdbc.sql("select count(*) from identity.password_reset_tokens")
                .query(Long.class).single()).isZero();
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    /** Written straight to the table: this is about rows, and registering through the API would prove nothing. */
    private UUID registerUser() {
        UUID id = UuidV7.generate();
        Instant now = Instant.now();
        jdbc.sql("""
                        insert into identity.users (id, email, password_hash, name, created_at, updated_at)
                        values (?, ?, '{bcrypt}$2a$12$irrelevant', 'Ada', ?, ?)""")
                .params(id, "purge-" + id + "@example.com", at(now), at(now))
                .update();
        return id;
    }

    private String token(UUID userId, Instant expiresAt, Instant usedAt) {
        String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.sql("""
                        insert into identity.password_reset_tokens (token_hash, user_id, expires_at, used_at)
                        values (?, ?, ?, ?)""")
                .params(hash, userId, at(expiresAt), usedAt == null ? null : at(usedAt))
                .update();
        return hash;
    }

    private boolean exists(String tokenHash) {
        return jdbc.sql("select count(*) from identity.password_reset_tokens where token_hash = ?")
                .param(tokenHash)
                .query(Long.class)
                .single() == 1L;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
