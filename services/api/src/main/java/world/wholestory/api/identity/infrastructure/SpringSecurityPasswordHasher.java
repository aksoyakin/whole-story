package world.wholestory.api.identity.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import world.wholestory.api.identity.application.PasswordHasher;
import world.wholestory.api.identity.domain.Password;
import world.wholestory.api.identity.domain.PasswordHash;

/**
 * Adapter over Spring Security's {@code DelegatingPasswordEncoder}, so every stored hash carries its algorithm
 * prefix and the algorithm can be replaced without a data migration (ADR 0018).
 */
@Component
@RequiredArgsConstructor
class SpringSecurityPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    @Override
    public PasswordHash hash(Password password) {
        return PasswordHash.of(encoder.encode(password.value()));
    }

    @Override
    public boolean matches(Password password, PasswordHash hash) {
        return encoder.matches(password.value(), hash.value());
    }
}
