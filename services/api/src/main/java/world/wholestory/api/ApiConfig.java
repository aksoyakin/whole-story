package world.wholestory.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * @see OutboxResubmission for what the schedule is for
 */
@Configuration
@EnableScheduling
// Spring Modulith's @ApplicationModuleListener is an @Async listener, and Boot does not switch @Async on by
// itself: without this the password reset mail would be sent on the request thread.
@EnableAsync
class ApiConfig {

    /** Injected rather than read statically, so that time can be fixed in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
