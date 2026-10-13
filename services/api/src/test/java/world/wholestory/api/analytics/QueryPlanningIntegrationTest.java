package world.wholestory.api.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import world.wholestory.api.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every connection this service hands out plans each statement for the arguments it was given.
 * <p>
 * The driver promotes a statement to a server-side prepared one after its fifth execution, and PostgreSQL may
 * then settle on a single generic plan for every later set of arguments. A reporting query is the shape that
 * suffers most, because what it has to read depends entirely on which site and how wide a date range it was
 * handed — measured against a fifty million row corpus, a thirty-day dashboard cost 10.4 seconds on generic
 * plans and 7.9 on custom ones, and the summary query alone went from 865 ms to 375 ms
 * (docs/performance.md).
 * <p>
 * This is a line of configuration whose absence is completely silent: nothing fails, no log says anything,
 * the numbers are simply worse, and the first four executions of any statement look identical either way —
 * which is exactly how the original measurement missed it. Hence a test rather than a comment.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QueryPlanningIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void everyConnectionPlansStatementsForTheArgumentsItWasGiven() {
        String mode = jdbc.sql("show plan_cache_mode").query(String.class).single();

        assertThat(mode)
                .as("a pooled connection must disable generic plans; see spring.datasource.hikari.connection-init-sql")
                .isEqualTo("force_custom_plan");
    }
}
