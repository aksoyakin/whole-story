package world.wholestory.api.site.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.wholestory.api.site.domain.Site;
import world.wholestory.api.site.domain.Timezone;

import java.time.Clock;
import java.time.Instant;

/**
 * Changes what a site owner may decide about their own site.
 * <p>
 * Nothing is published. Neither of these settings changes what is collected — the timezone only decides how the
 * stored events are grouped when they are read (D-020), and sharing only decides who may read them — so ingest
 * and processor have no reason to hear about either (D-122).
 */
@Service
@RequiredArgsConstructor
public class UpdateSiteSettings {

    private final SiteRepository sites;
    private final ReadableSites readableSites;
    private final Clock clock;

    @Transactional
    public SiteSummary update(UpdateSiteSettingsCommand command) {
        Site site = readableSites.require(command.siteId(), command.actingUserId());

        // An unknown zone name is refused by the value object, so a site cannot be left reporting in a zone the
        // JDK has never heard of.
        Instant now = clock.instant();
        site.changeTimezone(Timezone.of(command.timezone()), now);
        if (command.publicDashboard()) {
            site.enableSharing(now);
        } else {
            site.disableSharing(now);
        }

        sites.save(site);
        return SiteMapper.toSummary(site);
    }
}
