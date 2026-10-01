package world.wholestory.api.site.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.shared.security.AuthenticatedUser;
import world.wholestory.api.site.application.ListSites;
import world.wholestory.api.site.application.RegisterSite;
import world.wholestory.api.site.application.RegisterSiteCommand;
import world.wholestory.api.site.application.RemoveSite;

import java.util.List;
import java.util.UUID;

/** Every call is answered for the signed-in user; which organization they may act in is checked by the use case. */
@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
class SiteController {

    private final RegisterSite registerSite;
    private final ListSites listSites;
    private final RemoveSite removeSite;

    @PostMapping
    ResponseEntity<SiteResponse> register(@Valid @RequestBody RegisterSiteRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        SiteResponse response = SiteResponseMapper.toResponse(registerSite.register(new RegisterSiteCommand(
                request.organizationId(), principal.getUserId(), request.domain(), request.timezone())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    List<SiteResponse> list(@RequestParam UUID organizationId,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return listSites.of(organizationId, principal.getUserId()).stream()
                .map(SiteResponseMapper::toResponse)
                .toList();
    }

    @DeleteMapping("/{siteId}")
    ResponseEntity<Void> remove(@PathVariable UUID siteId, @AuthenticationPrincipal AuthenticatedUser principal) {
        removeSite.remove(siteId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
