package world.wholestory.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ApiConfig {

    /** Injected rather than read statically, so that time can be fixed in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
