package world.wholestory.api.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import world.wholestory.api.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scrape endpoint, over real HTTP, and on a port no public route reaches.
 * <p>
 * The gauge asserted here is the other half of two decisions. Mail was taken out of the health endpoint
 * because the indicator dialled SMTP on every check (D-127), and a broker that is briefly away leaves a
 * publication behind (D-110, D-115); in both cases the record of the failure is an incomplete row in
 * {@code platform.event_publication}, and until this was published nothing could read it.
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
    void publishesHowManyPublicationsAreStillWaiting() {
        String body = call(managementPort).body();

        assertThat(body).contains("outbox_publications_incomplete");
        // Nothing is pending in a context that has just started: the value is a number, not an absence.
        assertThat(body).containsPattern("outbox_publications_incomplete \\d");
    }

    /**
     * Not 404 as in ingest's equivalent test: api sits behind a security chain, which refuses the request
     * before routing ever looks for a handler. Either way the port the application is served on does not hand
     * out metrics — which is the property worth holding, so it is asserted as the property and not as a code.
     */
    @Test
    void doesNotServeMetricsOnThePortTheApplicationIsServedOn() {
        var refused = call(servicePort);
        assertThat(refused.statusCode()).isNotEqualTo(200);
        assertThat(refused.body()).doesNotContain("outbox_publications_incomplete");

        assertThat(call(managementPort).statusCode()).isEqualTo(200);
    }

    private static java.net.http.HttpResponse<String> call(int port) {
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/actuator/prometheus"))
                    .build();
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
