package world.wholestory.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * @see OutboxResubmission for what the schedule is for
 */
@Configuration
@EnableScheduling
class ApiConfig {

    /** Injected rather than read statically, so that time can be fixed in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
