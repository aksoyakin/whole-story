package world.wholestory.api.identity.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.shared.security.AuthenticatedUser;
import world.wholestory.api.identity.application.FindUserProfile;
import world.wholestory.api.identity.application.RegisterUser;
import world.wholestory.api.identity.application.RegisterUserCommand;
import world.wholestory.api.identity.application.RegisteredUser;
import world.wholestory.api.shared.domain.UserId;

/**
 * Sign-up and sign-in. Only the Next server calls these; the browser never reaches api directly
 * (see ADR 0011 and ADR 0018). Signing out is an endpoint here rather than Spring Security's logout filter,
 * for the reason noted on {@code logoutHandler}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final RegisterUser registerUser;
    private final FindUserProfile userProfiles;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    /**
     * Logging out is an endpoint rather than Spring Security's logout filter, so that it appears in the
     * generated OpenAPI document like every other call the web client makes (D-049). Invalidating the session
     * is enough to clear the cookie: Spring Session expires it on the way out.
     */
    private final LogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @PostMapping("/register")
    ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        RegisteredUser registered = registerUser.register(
                new RegisterUserCommand(request.email(), request.name(), request.password()));

        // Signed in straight away: leaving someone on the login page right after they chose a password is a worse
        // first run. The token is built directly rather than re-authenticating, which would verify the hash twice.
        AuthenticatedUser principal = new AuthenticatedUser(registered.userId().value(), request.email(), null);
        startSession(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()), httpRequest, httpResponse);

        return ResponseEntity.status(HttpStatus.CREATED).body(profileOf(registered.userId()));
    }

    @PostMapping("/login")
    UserProfileResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        startSession(authentication, httpRequest, httpResponse);
        return profileOf(UserId.of(((AuthenticatedUser) authentication.getPrincipal()).getUserId()));
    }

    /** Idempotent on purpose: signing out must not be able to fail, with or without a live session. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logoutHandler.logout(httpRequest, httpResponse, contextHolderStrategy.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return profileOf(UserId.of(principal.getUserId()));
    }

    /**
     * Stores the authentication in a session of its own. The identifier is rotated first: a session that existed
     * while the visitor was anonymous must not carry over into their authenticated one (session fixation).
     */
    private void startSession(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private UserProfileResponse profileOf(UserId userId) {
        return userProfiles.byId(userId)
                .map(UserProfileResponseMapper::toResponse)
                .orElseThrow(() -> new IllegalStateException("no profile for authenticated user " + userId));
    }
}
