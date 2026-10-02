package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.EmailAddress;

/**
 * Port to the sessions a person has open, so that resetting a password can end every one of them.
 * <p>
 * Keyed by the address rather than the user id because that is what the session store indexes: the principal's
 * name, which here is the e-mail address (ADR 0018).
 */
public interface UserSessions {

    void revokeAll(EmailAddress email);
}
