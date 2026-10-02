package world.wholestory.api.identity.application;

/**
 * Port for getting a reset link to its owner. Narrow on purpose: this product sends one kind of mail, so there
 * is no generic mail abstraction to keep honest, and the day e-mail becomes a product surface — reports, team
 * invitations — the adapter behind this is the only thing that has to change (ADR 0021).
 * <p>
 * Implementations throw when the mail could not be handed over, so that the outbox keeps the publication and
 * tries again rather than reporting success for a mail nobody received.
 */
public interface PasswordResetMailer {

    void send(PasswordResetMail mail);
}
