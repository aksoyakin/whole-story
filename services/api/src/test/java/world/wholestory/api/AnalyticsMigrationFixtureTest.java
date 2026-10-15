package world.wholestory.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * api's test database is built from processor's real migrations, because the {@code api_*} views are the
 * contract between the two services (D-012). This asserts that it is built from <em>all</em> of them.
 * <p>
 * Written because the list had already fallen behind by two: it stopped at V5 while processor was on V7, and
 * nothing failed, because neither missing migration touched anything api reads. A fixture that is wrong
 * without being broken is the worst kind — the next migration to add a view api does read would have looked
 * like a bug in the code rather than a gap in the setup, which is exactly how it was found.
 * <p>
 * Compared as a set rather than in order: being complete is what this checks, and a wrong order fails loudly
 * on its own the moment a migration refers to something that does not exist yet.
 */
class AnalyticsMigrationFixtureTest {

    @Test
    void everyAnalyticsMigrationIsAppliedToTheTestDatabase() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("../processor/src/main/resources/db/migration"))) {
            List<String> onDisk = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .toList();
            assertThat(TestcontainersConfiguration.ANALYTICS_MIGRATIONS)
                    .containsExactlyInAnyOrderElementsOf(onDisk);
        }
    }
}
