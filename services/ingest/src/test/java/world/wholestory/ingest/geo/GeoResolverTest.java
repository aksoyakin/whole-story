package world.wholestory.ingest.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses MaxMind's own test database (fabricated records); see src/test/resources/geoip/README.md. */
class GeoResolverTest {

    private static final Path DATABASE = Path.of("src/test/resources/geoip/GeoIP2-City-Test.mmdb");

    private final GeoResolver resolver = new GeoResolver(DATABASE);

    @Test
    void resolvesCountryRegionAndCity() {
        GeoLocation location = resolver.resolve("2.125.160.216");

        assertThat(location.countryCode()).isEqualTo("GB");
        assertThat(location.subdivisionCode()).isEqualTo("GB-ENG");
        assertThat(location.cityGeonameId()).isEqualTo(2655045); // Boxford
    }

    /** The name comes out of the same lookup as the code, which is why reporting needs no second geo dataset. */
    @Test
    void resolvesTheNamesAlongsideTheCodes() {
        GeoLocation location = resolver.resolve("2.125.160.216");

        assertThat(location.cityName()).isEqualTo("Boxford");
        assertThat(location.subdivisionName()).isEqualTo("England");
    }

    @Test
    void aPlaceWithoutANameIsLeftEmptyRatherThanGuessed() {
        // Covered by the database at country level only.
        GeoLocation location = resolver.resolve("67.43.156.1");

        assertThat(location.countryCode()).isNotNull();
        assertThat(location.cityName()).isNull();
        assertThat(location.subdivisionName()).isNull();
    }

    @Test
    void prefixesTheRegionWithItsCountryBecauseIsoCodesAreNotUniqueOnTheirOwn() {
        // Sweden's Östergötland is "E", which is meaningless without the country.
        assertThat(resolver.resolve("89.160.20.112").subdivisionCode()).isEqualTo("SE-E");
    }

    @Test
    void usesTheSameRegionLevelAcrossCountries() {
        // A US state, not a county; the UK country, not the district.
        assertThat(resolver.resolve("216.160.83.56").subdivisionCode()).isEqualTo("US-WA");
        assertThat(resolver.resolve("175.16.199.1").subdivisionCode()).isEqualTo("CN-22");
    }

    @Test
    void returnsUnknownForAddressesTheDatabaseDoesNotCover() {
        assertThat(resolver.resolve("203.0.113.42")).isEqualTo(GeoLocation.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.1", "192.168.1.10", "172.16.0.1", "::1", "0.0.0.0", "169.254.1.1"})
    void doesNotLookUpPrivateOrLocalAddresses(String address) {
        assertThat(resolver.resolve(address)).isEqualTo(GeoLocation.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "not-an-ip", "", "1.2.3", "999.1.1.1"})
    void returnsUnknownForAnythingThatIsNotAnIpLiteral(String value) {
        // A hostname must never trigger a DNS lookup: X-Forwarded-For is attacker controlled.
        assertThat(resolver.resolve(value)).isEqualTo(GeoLocation.UNKNOWN);
    }

    @Test
    void returnsUnknownForANullAddress() {
        assertThat(resolver.resolve(null)).isEqualTo(GeoLocation.UNKNOWN);
    }

    @Test
    void exposesTheDatabaseBuildTimeForMonitoring() {
        assertThat(resolver.isLoaded()).isTrue();
        assertThat(resolver.builtAt()).isPresent();
    }

    @Test
    void keepsAcceptingEventsWhenTheDatabaseIsMissing(@TempDir Path directory) {
        GeoResolver withoutDatabase = new GeoResolver(directory.resolve("absent.mmdb"));

        assertThat(withoutDatabase.isLoaded()).isFalse();
        assertThat(withoutDatabase.builtAt()).isEmpty();
        assertThat(withoutDatabase.resolve("2.125.160.216")).isEqualTo(GeoLocation.UNKNOWN);
    }

    @Test
    void picksUpADatabaseThatAppearsLater(@TempDir Path directory) throws IOException {
        Path database = directory.resolve("GeoLite2-City.mmdb");
        GeoResolver resolver = new GeoResolver(database);
        assertThat(resolver.isLoaded()).isFalse();

        Files.copy(DATABASE, database); // as the geoipupdate sidecar would

        resolver.reloadWhenUpdated();

        assertThat(resolver.isLoaded()).isTrue();
        assertThat(resolver.resolve("2.125.160.216").countryCode()).isEqualTo("GB");
    }
}
