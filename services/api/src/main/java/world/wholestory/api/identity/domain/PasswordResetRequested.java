package world.wholestory.api.identity.domain;

import world.wholestory.api.shared.domain.DomainEvent;
import world.wholestory.api.shared.domain.UserId;

import java.time.Instant;

/**
 * Somebody asked for a reset link. Carried through the outbox so that the mail survives an SMTP server that is
 * briefly unreachable (ADR 0009).
 * <p>
 * It deliberately holds nothing but the identifier. The outbox serialises an event into
 * {@code platform.event_publication}, so anything in here is at rest in the database: a token in this record
 * would be a usable credential sitting in a table, which is the very thing storing only the hash prevents. The
 * token is therefore minted by the listener, at the moment the mail is sent, and the address is read from the
 * user then as well.
 */
public record PasswordResetRequested(UserId userId, Instant occurredAt) implements DomainEvent {
}
