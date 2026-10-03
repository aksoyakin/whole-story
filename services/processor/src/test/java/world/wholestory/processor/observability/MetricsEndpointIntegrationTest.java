package world.wholestory.processor.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import world.wholestory.processor.TestcontainersConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The scrape endpoint, over real HTTP, and on a port no public route reaches.
 *
 * @see world.wholestory.processor.consume.RawEventListener for what produces the number below
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
    void publishesTheCrawlerCount() {
        // Registered when the enricher is built, so it reads zero rather than being absent until the first bot.
        assertThat(scrape(managementPort)).contains("events_dropped_total");
    }

    /**
     * The number the dashboard watches to know whether the reports are current. It appears once the consumer
     * has joined its group, which happens a moment after the context is up.
     */
    @Test
    void publishesHowFarBehindTheConsumerIs() {
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(scrape(managementPort))
                        .contains("kafka_consumer_fetch_manager_records_lag_max"));
    }

    @Test
    void isNotReachableOnThePortTheWorldCanReach() {
        assertThat(status(servicePort)).isEqualTo(404);
        assertThat(status(managementPort)).isEqualTo(200);
    }

    private static String scrape(int port) {
        return call(port).body();
    }

    private static int status(int port) {
        return call(port).statusCode();
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
