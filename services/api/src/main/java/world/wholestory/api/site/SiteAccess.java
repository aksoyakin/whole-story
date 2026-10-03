package world.wholestory.api.site;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other contexts may ask Site Management. Reporting needs one thing: may this person read this site, and
 * what does it need to know about it to answer in the site's own terms.
 * <p>
 * Authorization and the site's settings come back together on purpose. Reporting needs the timezone for every
 * query — a day starts where the site says it does (D-020) — and asking twice would mean two round trips and a
 * window where the answers disagree.
 * <p>
 * Plain identifiers: Identity and Site Management share typed ids because both have a domain model, while
 * Analytics deliberately has none, being the read side.
 */
public interface SiteAccess {

    /** Empty when the site does not exist, was removed, or belongs to an organization the user is not in. */
    Optional<ReadableSite> readableBy(UUID siteId, UUID userId);

    /**
     * The site behind a public dashboard, addressed by its domain because that is what the shared URL carries.
     * <p>
     * Empty when the domain is not tracked, when its site was removed, when sharing is off — and when the name
     * is not a domain at all. One answer for all four, so that the URL cannot be used to find out which sites
     * exist or which of them have sharing switched off.
     */
    Optional<ReadableSite> publiclyReadable(String domain);

    /**
     * What this site counts as a conversion. Asked separately from {@link #readableBy} rather than carried on
     * {@code ReadableSite}, because that answer is needed on every reporting request and this one only by the
     * goals report — a dashboard makes ten of the former and one of the latter.
     * <p>
     * Access is <strong>not</strong> checked here: ask {@code readableBy} first, as the goals report does.
     */
    List<ReadableGoal> goalsOf(UUID siteId);
}
