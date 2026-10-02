package world.wholestory.api.identity.application;

import world.wholestory.api.shared.domain.UserId;

/**
 * Port that caps how often one account can be sent a reset link.
 * <p>
 * Without it the endpoint is a way to post mail to somebody else's inbox as often as you like, and a way to
 * burn through the sending allowance of the mail host. It is asked only about accounts that exist, so refusing
 * cannot be used to find out which addresses are registered.
 */
public interface PasswordResetAttempts {

    /** @return false when this account has already been sent as many links as the window allows */
    boolean allowAnother(UserId userId);
}
