package world.wholestory.api.site.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.site.SiteAccess;
import world.wholestory.api.site.domain.SiteNotFoundException;

/**
 * What a public dashboard needs to know about its site before it can ask for a single number: its name for the
 * heading, and its timezone, because a shared dashboard cuts its days where the site does and not where the
 * reader happens to be (D-020).
 * <p>
 * Open to anyone, like the reports beside it, and refusing the same way: a domain nobody tracks, a removed
 * site and one with sharing switched off are indistinguishable from here.
 */
@RestController
@RequestMapping("/api/public/sites/{domain}")
@RequiredArgsConstructor
class PublicSiteController {

    private final SiteAccess sites;

    @GetMapping
    SharedSiteResponse shared(@PathVariable String domain) {
        return sites.publiclyReadable(domain)
                .map(site -> new SharedSiteResponse(site.domain(), site.timezone()))
                .orElseThrow(SiteNotFoundException::new);
    }
}
