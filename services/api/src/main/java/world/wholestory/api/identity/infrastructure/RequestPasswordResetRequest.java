package world.wholestory.api.identity.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only a length bound here. Whether this is a well-formed address, and whether it belongs to an account, are
 * both answered silently: the endpoint must not say which.
 */
record RequestPasswordResetRequest(@NotBlank @Size(max = 254) String email) {
}
