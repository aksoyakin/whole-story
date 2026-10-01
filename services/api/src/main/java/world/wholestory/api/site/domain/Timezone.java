package world.wholestory.api.site.domain;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * The site's own timezone, used to decide where a day starts when hourly UTC rollups are summed (D-020).
 * Validated against the JDK's zone database, which {@code java.time} makes available to a pure domain.
 */
public record Timezone(String value) {

    public static final Timezone UTC = new Timezone("UTC");

    public Timezone {
        if (value == null || value.isBlank()) {
            throw new InvalidTimezoneException(value);
        }
        value = value.trim();
        try {
            ZoneId.of(value);
        } catch (DateTimeException e) {
            throw new InvalidTimezoneException(value);
        }
    }

    public static Timezone of(String value) {
        return new Timezone(value);
    }

    public ZoneId zoneId() {
        return ZoneId.of(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
