package world.wholestory.api.identity.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.domain.InvalidResetTokenException;
import world.wholestory.api.identity.domain.Password;
import world.wholestory.api.identity.domain.PasswordResetToken;
import world.wholestory.api.identity.domain.ResetToken;
import world.wholestory.api.identity.domain.User;

import java.time.Clock;
import java.time.Instant;

/**
 * Follows a reset link and sets a new password.
 * <p>
 * Everything here happens in one transaction, because a password that changed while its link stayed usable, or
 * while another outstanding link stayed usable, would be worse than not having changed at all.
 */
@Service
@RequiredArgsConstructor
public class ResetPassword {

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final UserSessions sessions;
    private final Clock clock;

    @Transactional
    public void reset(String rawToken, String newPassword) {
        // Checked first, so that a password the rules refuse does not spend the link on the way to being rejected.
        Password password = Password.of(newPassword);

        PasswordResetToken link = tokens.findByHash(ResetToken.of(rawToken).hash())
                .orElseThrow(InvalidResetTokenException::new);
        Instant now = clock.instant();
        link.redeem(now);
        tokens.save(link);

        User user = users.findById(link.getUserId()).orElseThrow(InvalidResetTokenException::new);
        user.changePassword(passwordHasher.hash(password), now);
        users.save(user);

        // Any other link that was asked for stops working: one reset, one link.
        tokens.findUnusedFor(user.getId()).forEach(other -> {
            other.invalidate(now);
            tokens.save(other);
        });

        // Whoever was signed in with the old password is signed out, which is the point of resetting it.
        sessions.revokeAll(user.getEmail());
    }
}
