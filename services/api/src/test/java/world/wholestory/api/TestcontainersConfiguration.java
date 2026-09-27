package world.wholestory.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

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
