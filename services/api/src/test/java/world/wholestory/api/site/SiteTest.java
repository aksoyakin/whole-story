package world.wholestory.api.site;

import org.junit.jupiter.api.Test;
import world.wholestory.api.shared.domain.DomainEvent;
import world.wholestory.api.shared.domain.OrganizationId;
import world.wholestory.api.site.domain.Domain;
import world.wholestory.api.site.domain.InvalidTimezoneException;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.SiteDataPurgeRequested;
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

    /**
     * Two announcements, because two services have to hear different things: ingest that the domain is free,
     * and the processor that the data collected under it may go.
     */
    @Test
    void removingASiteAnnouncesItTwiceAndClosesTheSharedDashboard() {
        Site site = register();
        site.pullRecordedEvents();

        site.remove(LATER);

        assertThat(site.isRemoved()).isTrue();
        assertThat(site.deletedAt()).contains(LATER);
        assertThat(site.isPublicDashboard()).isFalse();

        List<DomainEvent> events = site.pullRecordedEvents();
        assertThat(events).hasSize(2);
        assertThat(events).hasAtLeastOneElementOfType(SiteRemoved.class);
        assertThat(events).filteredOn(SiteDataPurgeRequested.class::isInstance)
                .singleElement()
                .satisfies(event -> {
                    SiteDataPurgeRequested purge = (SiteDataPurgeRequested) event;
                    assertThat(purge.siteId()).isEqualTo(site.getId());
                    assertThat(purge.occurredAt()).isEqualTo(LATER);
                });
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
    void theTimezoneCanBeMovedAfterwards() {
        Site site = register();
        site.pullRecordedEvents();

        site.changeTimezone(Timezone.of("Europe/Istanbul"), LATER);

        assertThat(site.getTimezone()).isEqualTo(Timezone.of("Europe/Istanbul"));
        assertThat(site.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void sharingIsOpenedAndClosedOnRequest() {
        Site site = register();
        assertThat(site.isPublicDashboard()).isFalse();

        site.enableSharing(LATER);
        assertThat(site.isPublicDashboard()).isTrue();
        assertThat(site.getUpdatedAt()).isEqualTo(LATER);

        site.disableSharing(LATER.plusSeconds(60));
        assertThat(site.isPublicDashboard()).isFalse();
    }

    /**
     * Neither setting changes what is collected, so ingest must never be told about one. A message here would be
     * read as an instruction about the domain itself (D-122).
     */
    @Test
    void changingSettingsAnnouncesNothing() {
        Site site = register();
        site.pullRecordedEvents();

        site.changeTimezone(Timezone.of("Asia/Kolkata"), LATER);
        site.enableSharing(LATER);
        site.disableSharing(LATER);

        assertThat(site.pullRecordedEvents()).isEmpty();
    }

    /** Saving a form without touching it must not look like a change to anyone reading updated_at. */
    @Test
    void settingTheValuesItAlreadyHasChangesNothing() {
        Site site = register();
        Instant registeredAt = site.getUpdatedAt();

        site.changeTimezone(Timezone.UTC, LATER);
        site.disableSharing(LATER);

        assertThat(site.getUpdatedAt()).isEqualTo(registeredAt);
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
