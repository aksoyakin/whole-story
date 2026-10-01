package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.Password;
import world.wholestory.api.identity.domain.PasswordHash;

/**
 * Port for turning a password into its stored form. The algorithm is deliberately outside the domain: it is an
 * operational choice that will change (ADR 0018), and the domain must not depend on Spring Security to express it.
 */
public interface PasswordHasher {

    PasswordHash hash(Password password);

    boolean matches(Password password, PasswordHash hash);
}
