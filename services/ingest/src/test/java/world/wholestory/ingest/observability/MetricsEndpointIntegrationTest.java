package world.wholestory.ingest.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import world.wholestory.ingest.TestcontainersConfiguration;
import world.wholestory.ingest.observability.MetricsEndpointIntegrationTest.Scrape;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scrape endpoint, over real HTTP.
 * <p>
 * Worth a test because its absence was silent for weeks: the three services asked for {@code prometheus} in
 * their exposure list while no registry was on the classpath, so the endpoint simply did not exist and the only
 * trace was a startup line saying two endpoints had been exposed instead of three.
 * <p>
 * The second case is the one that matters more. ingest is the service a public route points at, and this
 * endpoint says how much traffic every tracked site gets. It must not answer on the port that route can reach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class MetricsEndpointIntegrationTest {

    @LocalServerPort
    int servicePort;
    @LocalManagementPort
    int managementPort;

    @Test
    void publishesTheMetersItRecords() {
        String body = Scrape.from(managementPort).body();

        // Registered eagerly, so it is here whether or not anything has happened yet (D-059).
        assertThat(body).contains("geoip_database_loaded");
        assertThat(body).contains("geoip_database_age_seconds");
        // Boot's own HTTP timer, with the buckets a percentile needs; count alone cannot answer "how slow was
        // the slow tenth" and that is the question a load test asks.
        assertThat(body).contains("http_server_requests_seconds_bucket");
    }

    @Test
    void isNotReachableOnThePortTheWorldCanReach() {
        assertThat(Scrape.from(servicePort).status()).isEqualTo(404);
        assertThat(Scrape.from(managementPort).status()).isEqualTo(200);
    }

    /** A plain HTTP call: the point is the port, which MockMvc does not have. */
    record Scrape(int status, String body) {
        static Scrape from(int port) {
            try (var client = java.net.http.HttpClient.newHttpClient()) {
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:" + port + "/actuator/prometheus"))
                        .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                return new Scrape(response.statusCode(), response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
