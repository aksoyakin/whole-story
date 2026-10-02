package world.wholestory.api.identity.application;

import world.wholestory.api.identity.domain.EmailAddress;
import world.wholestory.api.identity.domain.PersonName;
import world.wholestory.api.identity.domain.ResetToken;

/**
 * What a reset mail needs to say. It carries the token itself, which is why this type exists only in memory
 * between minting the link and handing it to the mail server.
 */
public record PasswordResetMail(EmailAddress to, PersonName name, ResetToken token) {
}
