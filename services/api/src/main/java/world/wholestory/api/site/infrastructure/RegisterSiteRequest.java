package world.wholestory.api.site.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param timezone IANA zone name; null means UTC. The web sends the browser's zone as a suggestion.
 */
record RegisterSiteRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 253) String domain,
        @Size(max = 64) String timezone) {
}
