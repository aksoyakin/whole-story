package world.wholestory.api.site;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import world.wholestory.api.site.domain.Goal;
import world.wholestory.api.site.domain.GoalId;
import world.wholestory.api.site.domain.GoalTarget;
import world.wholestory.api.site.domain.GoalType;
import world.wholestory.api.site.domain.InvalidGoalException;
import world.wholestory.api.site.domain.SiteId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    void anEventGoalCarriesTheNameTheTrackerReports() {
        GoalTarget target = GoalTarget.event("  Signup  ");

        assertThat(target.type()).isEqualTo(GoalType.EVENT);
        assertThat(target.value()).isEqualTo("Signup");
        assertThat(target.isPage()).isFalse();
    }

    @Test
    void aPageGoalCarriesAPathAndMayUseTheWildcard() {
        assertThat(GoalTarget.page("/thanks").value()).isEqualTo("/thanks");
        assertThat(GoalTarget.page("/blog/*").value()).isEqualTo("/blog/*");
        assertThat(GoalTarget.page("/blog/*").isPage()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void anEventGoalWithoutANameIsRefused(String name) {
        assertThatThrownBy(() -> GoalTarget.event(name)).isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void anEventNameLongerThanIngestAcceptsIsRefused() {
        // Ingest rejects an event name over 120 characters, so such a goal could never match one.
        assertThat(GoalTarget.event("x".repeat(GoalTarget.MAX_EVENT_NAME_LENGTH)).value()).hasSize(120);
        assertThatThrownBy(() -> GoalTarget.event("x".repeat(GoalTarget.MAX_EVENT_NAME_LENGTH + 1)))
                .isInstanceOf(InvalidGoalException.class);
    }

    /**
     * Every one of these looks like something a person would type, and every one would count zero for ever.
     * Refusing them when the goal is defined is the whole reason this validation exists.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "thanks",                            // no leading slash
            "https://example.com/thanks",        // a whole URL
            "/thanks?ref=mail",                  // a query string is not part of the recorded path
            "/thanks#top",                       // nor is a fragment
            "/with a space",
            ""})
    void aPageGoalThatCouldNeverMatchIsRefused(String pattern) {
        assertThatThrownBy(() -> GoalTarget.page(pattern)).isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void theRefusalSaysWhatToWriteInstead() {
        assertThatThrownBy(() -> GoalTarget.page("thanks"))
                .hasMessageContaining("/thanks")
                .hasMessageContaining("/blog/*");
    }

    @Test
    void aGoalKnowsWhichSiteItBelongsTo() {
        SiteId site = SiteId.of(UUID.randomUUID());
        Goal goal = Goal.define(GoalId.of(UUID.randomUUID()), site, GoalTarget.event("Signup"), NOW);

        assertThat(goal.belongsTo(site)).isTrue();
        assertThat(goal.belongsTo(SiteId.of(UUID.randomUUID()))).isFalse();
        assertThat(goal.getCreatedAt()).isEqualTo(NOW);
    }
}
