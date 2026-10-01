package world.wholestory.api.identity.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.EmailAlreadyRegisteredException;
import world.wholestory.api.identity.domain.Organization;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.identity.domain.Password;
import world.wholestory.api.identity.domain.PasswordHash;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.User;
import world.wholestory.api.shared.domain.UserId;
import world.wholestory.contracts.UuidV7;

import java.time.Clock;
import java.time.Instant;

/**
 * Registration creates a user and, with it, the single-person organization that owns their sites (D-004).
 * Both writes are one transaction: an account without an organization could not add a site and would be a
 * dead end the user cannot repair.
 */
@Service
@RequiredArgsConstructor
public class RegisterUser {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Transactional
    public RegisteredUser register(RegisterUserCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        PersonName name = PersonName.of(command.name());
        Password password = Password.of(command.password());

        // A friendly answer for the ordinary case. The unique index is what actually guarantees it, because two
        // requests can pass this check at the same time; the adapter translates that collision into the same error.
        if (users.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException(email);
        }

        Instant now = clock.instant();
        PasswordHash hash = passwordHasher.hash(password);
        User user = User.register(UserId.of(UuidV7.generate(now)), email, name, hash, now);
        Organization organization = Organization.createFor(
                OrganizationId.of(UuidV7.generate(now)), user.getId(), name, now);

        users.save(user);
        organizations.save(organization);
        return new RegisteredUser(user.getId(), organization.getId());
    }
}
