package world.wholestory.api.identity.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import world.wholestory.api.identity.domain.PasswordResetRequested;
import world.wholestory.api.identity.domain.PasswordResetToken;
import world.wholestory.api.identity.domain.ResetToken;
import world.wholestory.api.identity.domain.User;

import java.time.Clock;

/**
 * Mints a reset link and sends it, once the request it belongs to has committed.
 * <p>
 * The token is created here rather than where the request came in, and that is the point: the event that makes
 * this retryable is stored in {@code platform.event_publication}, so a token put in the event would be a working
 * credential at rest in the database. Minting at send time keeps the secret in memory and leaves only its hash
 * behind.
 * <p>
 * Failing is allowed to fail: the listener runs in a transaction of its own, so an unreachable mail server rolls
 * the token back and leaves the publication incomplete, and {@code OutboxResubmission} comes back to it with a
 * fresh token. A link that was minted but never sent would otherwise be a link nobody can use.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendPasswordResetMail {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordResetMailer mailer;
    private final Clock clock;

    @ApplicationModuleListener
    void on(PasswordResetRequested requested) {
        User user = users.findById(requested.userId()).orElse(null);
        if (user == null) {
            // The account went away between asking and sending; there is nobody to write to.
            log.warn("Dropping a password reset for {}: the account no longer exists", requested.userId());
            return;
        }
        ResetToken token = ResetToken.generate();
        tokens.save(PasswordResetToken.issue(token.hash(), user.getId(), clock.instant()));
        mailer.send(new PasswordResetMail(user.getEmail(), user.getName(), token));
    }
}
