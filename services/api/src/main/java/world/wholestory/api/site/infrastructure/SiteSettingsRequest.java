package world.wholestory.api.site.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Every setting, every time: this replaces a site's settings rather than patching them.
 *
 * @param timezone        IANA zone name; whether the JDK knows it is the domain's business, not this one's
 * @param publicDashboard boxed and required on purpose — a primitive would read an omitted field as
 *                        {@code false} and quietly close a shared dashboard the caller never meant to touch
 */
record SiteSettingsRequest(
        @NotBlank @Size(max = 64) String timezone,
        @NotNull Boolean publicDashboard) {
}
