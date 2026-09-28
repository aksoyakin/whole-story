package world.wholestory.ingest.geo;

import com.maxmind.db.CHMCache;
import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Subdivision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves country, region and city from an IP address against a MaxMind GeoLite2 City database.
 * <p>
 * The database file is maintained out of process (the {@code geoipupdate} sidecar in production) and is picked up
 * again when it changes. A missing or broken database is never fatal: events are still accepted without a location,
 * because losing an event is worse than losing its geography.
 */
@Slf4j
public class GeoResolver {

    private final Path databasePath;
    private final AtomicReference<Database> database = new AtomicReference<>();

    public GeoResolver(Path databasePath) {
        this.databasePath = databasePath;
        load();
    }

    public GeoLocation resolve(String ipAddress) {
        Database current = database.get();
        if (current == null || ipAddress == null) {
            return GeoLocation.UNKNOWN;
        }
        InetAddress address;
        try {
            // ofLiteral never performs a DNS lookup, so a forged X-Forwarded-For cannot trigger one.
            address = InetAddress.ofLiteral(ipAddress);
        } catch (IllegalArgumentException e) {
            return GeoLocation.UNKNOWN;
        }
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return GeoLocation.UNKNOWN;
        }
        try {
            return current.reader().tryCity(address).map(GeoResolver::toLocation).orElse(GeoLocation.UNKNOWN);
        } catch (Exception e) {
            // Only the exception type is logged: some GeoIP2 messages embed the address, which must never be logged.
            log.warn("GeoIP lookup failed ({}); continuing without a location", e.getClass().getSimpleName());
            return GeoLocation.UNKNOWN;
        }
    }

    public boolean isLoaded() {
        return database.get() != null;
    }

    /** Build time of the loaded database, used to alert on a database that stopped being updated. */
    public Optional<Instant> builtAt() {
        return Optional.ofNullable(database.get()).map(Database::builtAt);
    }

    @Scheduled(fixedDelay = 15, initialDelay = 15, timeUnit = TimeUnit.MINUTES)
    void reloadWhenUpdated() {
        Database current = database.get();
        Instant modifiedAt = lastModified();
        if (modifiedAt != null && (current == null || !modifiedAt.equals(current.modifiedAt()))) {
            load();
        }
    }

    private void load() {
        Instant modifiedAt = lastModified();
        if (modifiedAt == null) {
            log.warn("No GeoIP database at {}; events will be stored without a location", databasePath);
            return;
        }
        try {
            DatabaseReader reader = new DatabaseReader.Builder(databasePath.toFile())
                    .fileMode(Reader.FileMode.MEMORY_MAPPED) // 60+ MB stays off the heap
                    .withCache(new CHMCache())
                    .build();
            // The previous reader is not closed: closing unmaps memory that in-flight lookups may still be reading.
            // Reloads happen at most weekly, so letting the garbage collector unmap it is the safer trade.
            database.set(new Database(reader, modifiedAt, reader.metadata().buildTime()));
            log.info("Loaded GeoIP database {} (built {})", databasePath, reader.metadata().buildTime());
        } catch (Exception e) {
            log.error("Could not load the GeoIP database at {}", databasePath, e);
        }
    }

    private Instant lastModified() {
        try {
            return Files.getLastModifiedTime(databasePath).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static GeoLocation toLocation(CityResponse response) {
        String countryCode = response.country().isoCode();
        return new GeoLocation(countryCode, subdivisionCode(countryCode, response.subdivisions()), cityGeonameId(response));
    }

    /**
     * ISO 3166-2 codes are only unique together with their country ({@code E} is a Swedish county), so the country
     * is kept as a prefix. The least specific subdivision is used, which is the same level across countries:
     * a US state, a Turkish province, a UK country.
     */
    private static String subdivisionCode(String countryCode, List<Subdivision> subdivisions) {
        if (countryCode == null || subdivisions.isEmpty() || subdivisions.getFirst().isoCode() == null) {
            return null;
        }
        return countryCode + "-" + subdivisions.getFirst().isoCode();
    }

    private static Integer cityGeonameId(CityResponse response) {
        Long geonameId = response.city().geonameId();
        return geonameId == null ? null : geonameId.intValue();
    }

    private record Database(DatabaseReader reader, Instant modifiedAt, Instant builtAt) {
    }
}
