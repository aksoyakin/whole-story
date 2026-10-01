package world.wholestory.api.identity.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of an organization, the tenant everything else hangs from (D-003). */
public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "an organization id is required");
    }

    public static OrganizationId of(UUID value) {
        return new OrganizationId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
