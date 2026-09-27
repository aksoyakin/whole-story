package world.wholestory.processor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Clock;

@Configuration
@EnableScheduling
class ProcessorConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * A failing batch (e.g. database down) is retried until it succeeds instead of being skipped:
     * pausing ingestion processing is better than silently losing analytics data.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxInterval(30_000);
        return new DefaultErrorHandler(backOff);
    }
}
