package world.wholestory.api.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import world.wholestory.api.identity.domain.Password;

record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = Password.MIN_LENGTH, max = Password.MAX_LENGTH) String password) {
}
