package world.wholestory.api.site.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a tracked site. Stays in this context: no other context has to name a site, and a typed id only
 * moves to the shared kernel once a second context does.
 */
public record SiteId(UUID value) {

    public SiteId {
        Objects.requireNonNull(value, "a site id is required");
    }

    public static SiteId of(UUID value) {
        return new SiteId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
