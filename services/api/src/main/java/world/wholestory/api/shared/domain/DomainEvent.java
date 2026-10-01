package world.wholestory.api.shared.domain;

/**
 * Something that happened inside an aggregate. Aggregates record these; the application layer publishes them
 * after the change is persisted, which is what makes the outbox possible (ADR 0009).
 */
public interface DomainEvent {
}
