package world.wholestory.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * The production role and schema script, plus processor's analytics migrations: api reads the {@code api_*}
     * views at runtime (D-012) and depends on processor having created them, so its tests need them too. The
     * files are the real ones, which means a change to that contract shows up here.
     */
    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6-alpine")
                .withDatabaseName("wholestory")
                .withEnv("API_DB_PASSWORD", "api")
                .withEnv("PROCESSOR_DB_PASSWORD", "processor")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../../infra/postgres/init/01-roles-and-schemas.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-roles-and-schemas.sh")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../processor/src/main/resources/db/migration/V1__analytics.sql"),
                        "/docker-entrypoint-initdb.d/02-analytics.sql")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(
                                "../processor/src/main/resources/db/migration/V2__bounce_definition.sql"),
                        "/docker-entrypoint-initdb.d/03-analytics-bounce.sql")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../processor/src/main/resources/db/migration/V3__geo_names.sql"),
                        "/docker-entrypoint-initdb.d/04-analytics-geo-names.sql");
    }

    /** The outbox externalizes site events to Kafka (ADR 0009). */
    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.1");
    }

    /** Sessions live in Redis (ADR 0018), so the context needs a real one. */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:8.8.3-alpine").withExposedPorts(6379);
    }

    /** Connect as the api's own role, not as the container superuser (D-037). */
    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "wholestory_api");
            registry.add("spring.datasource.password", () -> "api");
        };
    }
}
