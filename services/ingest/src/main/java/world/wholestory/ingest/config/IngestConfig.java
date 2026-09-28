package world.wholestory.ingest.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import world.wholestory.contracts.Topics;
import world.wholestory.ingest.geo.GeoResolver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(IngestProperties.class)
class IngestConfig implements WebMvcConfigurer {

    @Bean
    NewTopic rawEventsTopic(IngestProperties properties) {
        return TopicBuilder.name(Topics.RAW_EVENTS).partitions(properties.topicPartitions()).build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    GeoResolver geoResolver(IngestProperties properties) {
        return new GeoResolver(properties.geoipDatabase());
    }

    /**
     * A stale or missing GeoIP database is reported through metrics rather than the health endpoint: the container
     * must stay healthy and keep accepting events, since a location is a nice-to-have on an event.
     */
    @Bean
    MeterBinder geoipMetrics(GeoResolver resolver) {
        return registry -> {
            Gauge.builder("geoip.database.loaded", () -> resolver.isLoaded() ? 1 : 0)
                    .description("1 when a GeoIP database is loaded, 0 otherwise")
                    .register(registry);
            Gauge.builder("geoip.database.age", () -> resolver.builtAt()
                            .map(builtAt -> (double) Duration.between(builtAt, Instant.now()).toSeconds())
                            .orElse(Double.NaN))
                    .description("Age of the loaded GeoIP database")
                    .baseUnit("seconds")
                    .register(registry);
        };
    }

    /** The tracker runs on customer sites; origins are validated against registered domains instead. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/event").allowedOrigins("*").allowedMethods("POST");
    }
}
