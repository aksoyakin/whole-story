package world.wholestory.processor;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import world.wholestory.contracts.Topics;

/** Real PostgreSQL (with the production role/schema init script), Kafka and Redis. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6-alpine")
                .withDatabaseName("wholestory")
                .withEnv("API_DB_PASSWORD", "api")
                .withEnv("PROCESSOR_DB_PASSWORD", "processor")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../../infra/postgres/init/01-roles-and-schemas.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-roles-and-schemas.sh");
    }

    /** Connect as the processor's own role, not as the container superuser (D-037). */
    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "wholestory_processor");
            registry.add("spring.datasource.password", () -> "processor");
        };
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.1");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:8.8.3-alpine").withExposedPorts(6379);
    }

    /** In production the topic is declared by ingest, its producer. */
    @Bean
    NewTopic rawEventsTopic() {
        return TopicBuilder.name(Topics.RAW_EVENTS).partitions(1).build();
    }
}
