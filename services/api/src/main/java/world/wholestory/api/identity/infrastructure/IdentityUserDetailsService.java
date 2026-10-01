package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.shared.security.AuthenticatedUser;
import world.wholestory.api.identity.application.UserRepository;
import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.InvalidEmailAddressException;
import world.wholestory.api.identity.domain.User;

import java.util.Optional;

/** Bridges Spring Security's authentication to the identity context. */
@Service
@RequiredArgsConstructor
class IdentityUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return parse(username)
                .flatMap(users::findByEmail)
                .map(IdentityUserDetailsService::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("no account for this e-mail address"));
    }

    /**
     * A malformed address is simply an unknown account. Telling the caller that the input was invalid rather than
     * unregistered would hand them a way to tell the two apart.
     */
    private static Optional<EmailAddress> parse(String username) {
        try {
            return Optional.of(EmailAddress.of(username));
        } catch (InvalidEmailAddressException e) {
            return Optional.empty();
        }
    }

    private static AuthenticatedUser toPrincipal(User user) {
        return new AuthenticatedUser(user.getId().value(), user.getEmail().value(), user.getPasswordHash().value());
    }
}
