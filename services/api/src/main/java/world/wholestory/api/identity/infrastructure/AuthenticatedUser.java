package world.wholestory.api.identity.infrastructure;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The principal stored in the session. It carries identifiers and nothing else: the session is serialised into
 * Redis, so a domain object here would tie the stored format to the model's shape.
 * <p>
 * Credentials are erased by Spring Security after authentication, so the hash does not reach Redis.
 */
@Getter
@EqualsAndHashCode(of = "userId")
public final class AuthenticatedUser implements UserDetails, Serializable {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final UUID userId;
    private final String username;
    private final String password;

    AuthenticatedUser(UUID userId, String username, String password) {
        this.userId = userId;
        this.username = username;
        this.password = password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Roles are per organization, so they are not a property of the principal. Site authorization comes with
        // Site Management, where the organization that owns the site is known.
        return AUTHORITIES;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser(" + userId + ")";
    }
}
