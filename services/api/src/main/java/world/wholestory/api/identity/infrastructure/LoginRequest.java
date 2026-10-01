package world.wholestory.api.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;

record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
