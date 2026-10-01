package world.wholestory.api.identity.infrastructure;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import world.wholestory.api.identity.domain.Password;

/** Shape is checked here; the rules themselves live in the domain's value objects. */
record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = Password.MIN_LENGTH, max = Password.MAX_LENGTH) String password) {
}
