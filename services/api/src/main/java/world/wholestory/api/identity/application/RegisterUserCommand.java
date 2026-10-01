package world.wholestory.api.identity.application;

/**
 * Raw input for a registration. Primitives in, value objects inside: the use case builds them, so every
 * validation failure is raised in one place instead of at each caller.
 */
public record RegisterUserCommand(String email, String name, String password) {
}
