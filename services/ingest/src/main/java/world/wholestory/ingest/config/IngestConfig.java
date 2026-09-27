package world.wholestory.ingest.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import world.wholestory.contracts.Topics;

import java.time.Clock;

@Configuration
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

    /** The tracker runs on customer sites; origins are validated against registered domains instead. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/event").allowedOrigins("*").allowedMethods("POST");
    }
}
