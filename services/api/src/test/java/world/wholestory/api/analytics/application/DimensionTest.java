package world.wholestory.api.analytics.application;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DimensionTest {

    /**
     * The half of a contract whose other half lives in processor, in {@code VisitorDailyRollup} and the test
     * that pins what it writes. A dimension marked here is one a report may be answered from the daily
     * rollup; marking one the rollup does not compute would not fail anywhere — it would return the two
     * partial days at the edges of a range and leave out everything between them.
     * <p>
     * So the list is written out rather than derived, and no property of the dimension implies it. Deriving
     * it from {@code source()} was exactly the bug this guards against: it is neither "the session-backed
     * ones" — every one of the fourteen is, except the page — nor "the ones from events", since the page is
     * the only one of those and it is in.
     */
    @Test
    void onlyTheDimensionsTheDailyRollupComputesMayBeReadFromIt() {
        assertThat(Arrays.stream(Dimension.values()).filter(Dimension::rolledUp).toList())
                .containsExactly(Dimension.PAGE, Dimension.SOURCE, Dimension.COUNTRY, Dimension.REGION,
                        Dimension.CITY, Dimension.BROWSER, Dimension.OS, Dimension.DEVICE);
    }

    /**
     * The ones still counted live, named so that adding one to the rollup is a decision rather than a slip. A
     * row per entry page or per campaign per day would make the rollup a second copy of the sessions.
     */
    @Test
    void thePerVisitDimensionsAreCountedLive() {
        assertThat(Arrays.stream(Dimension.values()).filter(d -> !d.rolledUp()).toList())
                .containsExactly(Dimension.ENTRY_PAGE, Dimension.EXIT_PAGE,
                        Dimension.UTM_SOURCE, Dimension.UTM_MEDIUM, Dimension.UTM_CAMPAIGN,
                        Dimension.UTM_CONTENT, Dimension.UTM_TERM);
    }
}
