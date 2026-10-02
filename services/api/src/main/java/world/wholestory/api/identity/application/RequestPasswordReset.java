package world.wholestory.api.identity.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.InvalidEmailAddressException;
import world.wholestory.api.identity.domain.PasswordResetRequested;
import world.wholestory.api.identity.domain.User;

import java.time.Clock;
import java.util.Optional;

/**
 * Asks for a reset link.
 * <p>
 * It reports nothing back, whatever happens: an address that is registered, one that is not, and one that is not
 * an address at all all look the same from outside, so this form cannot be used to find out who has an account.
 * The caller is told only that a mail is on its way if the address was ours.
 * <p>
 * The work of sending is handed to {@link SendPasswordResetMail} through an event rather than done here. That is
 * what puts it in the outbox, and it is also why the response does not wait for a mail server.
 */
@Service
@RequiredArgsConstructor
public class RequestPasswordReset {

    private final UserRepository users;
    private final PasswordResetAttempts attempts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void request(String emailAddress) {
        Optional<User> account = parse(emailAddress).flatMap(users::findByEmail);
        if (account.isEmpty()) {
            return;
        }
        User user = account.get();
        if (!attempts.allowAnother(user.getId())) {
            return;
        }
        events.publishEvent(new PasswordResetRequested(user.getId(), clock.instant()));
    }

    /** A malformed address is simply an address we do not have, as it is when signing in. */
    private static Optional<EmailAddress> parse(String value) {
        try {
            return Optional.of(EmailAddress.of(value));
        } catch (InvalidEmailAddressException e) {
            return Optional.empty();
        }
    }
}
