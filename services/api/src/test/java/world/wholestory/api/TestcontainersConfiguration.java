package world.wholestory.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Where processor keeps the migrations that build the schema api reads through views (D-012). */
    private static final String MIGRATIONS = "../processor/src/main/resources/db/migration/";

    /**
     * Every analytics migration, in the order they have to run.
     * <p>
     * Named one by one rather than taken from the directory, because the container's init directory runs
     * whatever it finds in alphabetical order and these file names do not sort that way for long: V10 comes
     * before V2. The copy below numbers them instead, so the order is stated here and nowhere else.
     * <p>
     * Hand-maintained lists drift, and this one had: it stopped at V5 while processor was on V7. Nothing
     * noticed because neither missing migration touched anything api reads — V6 only resets a watermark and
     * V7 adds a table only processor uses — so the fixture was wrong without being broken, until V8 added a
     * view api does read. {@code AnalyticsMigrationFixtureTest} now fails when this list falls behind.
     */
    static final List<String> ANALYTICS_MIGRATIONS = List.of(
            "V1__analytics.sql",
            "V2__bounce_definition.sql",
            "V3__geo_names.sql",
            "V4__visitor_daily.sql",
            "V5__visitor_daily_pages.sql",
            "V6__visitor_daily_entry_exit.sql",
            "V7__visitor_daily_dirty_days.sql",
            "V8__raw_data_retention.sql");

    /**
     * The production role and schema script, plus processor's analytics migrations: api reads the {@code api_*}
     * views at runtime (D-012) and depends on processor having created them, so its tests need them too. The
     * files are the real ones, which means a change to that contract shows up here.
     */
    @Bean
    PostgreSQLContainer postgres() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine")
                .withDatabaseName("wholestory")
                .withEnv("API_DB_PASSWORD", "api")
                .withEnv("PROCESSOR_DB_PASSWORD", "processor")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../../infra/postgres/init/01-roles-and-schemas.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-roles-and-schemas.sh");
        int order = 2;
        for (String migration : ANALYTICS_MIGRATIONS) {
            postgres = postgres.withCopyFileToContainer(
                    MountableFile.forHostPath(MIGRATIONS + migration),
                    "/docker-entrypoint-initdb.d/%02d-%s".formatted(order++, migration));
        }
        return postgres;
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
