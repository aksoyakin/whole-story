package world.wholestory.api.site;

import org.junit.jupiter.api.Test;
import world.wholestory.api.shared.domain.DomainEvent;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.site.domain.Domain;
import world.wholestory.api.site.domain.InvalidTimezoneException;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteId;
import world.wholestory.api.site.domain.SiteRegistered;
import world.wholestory.api.site.domain.SiteRemoved;
import world.wholestory.api.site.domain.Timezone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void registeringASiteAnnouncesIt() {
        Site site = register();

        List<DomainEvent> events = site.pullRecordedEvents();

        assertThat(events).singleElement().isInstanceOf(SiteRegistered.class);
        SiteRegistered registered = (SiteRegistered) events.getFirst();
        assertThat(registered.domain()).isEqualTo(Domain.of("example.com"));
        assertThat(registered.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void theEventsAreHandedOverOnlyOnce() {
        Site site = register();
        site.pullRecordedEvents();

        assertThat(site.pullRecordedEvents()).isEmpty();
    }

    @Test
    void removingASiteAnnouncesItAndClosesTheSharedDashboard() {
        Site site = register();
        site.pullRecordedEvents();

        site.remove(LATER);

        assertThat(site.isRemoved()).isTrue();
        assertThat(site.deletedAt()).contains(LATER);
        assertThat(site.isPublicDashboard()).isFalse();
        assertThat(site.pullRecordedEvents()).singleElement().isInstanceOf(SiteRemoved.class);
    }

    /** Ingest acts on every message it reads, so announcing a removal twice would be a second instruction. */
    @Test
    void removingATwiceRemovedSiteAnnouncesNothing() {
        Site site = register();
        site.remove(LATER);
        site.pullRecordedEvents();

        site.remove(LATER.plusSeconds(60));

        assertThat(site.pullRecordedEvents()).isEmpty();
        assertThat(site.deletedAt()).contains(LATER);
    }

    @Test
    void aSiteNeedsAZoneTheJdkKnows() {
        assertThat(Timezone.of("Europe/Istanbul").zoneId().getId()).isEqualTo("Europe/Istanbul");
        assertThatThrownBy(() -> Timezone.of("Europe/Atlantis")).isInstanceOf(InvalidTimezoneException.class);
        assertThatThrownBy(() -> Timezone.of(" ")).isInstanceOf(InvalidTimezoneException.class);
    }

    private static Site register() {
        return Site.register(
                SiteId.of(UUID.randomUUID()),
                OrganizationId.of(UUID.randomUUID()),
                Domain.of("example.com"),
                Timezone.UTC,
                NOW);
    }
}
