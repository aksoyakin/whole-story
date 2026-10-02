package world.wholestory.api.identity.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.wholestory.api.identity.application.RequestPasswordReset;
import world.wholestory.api.identity.application.ResetPassword;

/**
 * Forgotten passwords. Both calls are open, because whoever needs them cannot sign in by definition.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
class PasswordResetController {

    private final RequestPasswordReset requestPasswordReset;
    private final ResetPassword resetPassword;

    /**
     * Always answers the same. A registered address, an unregistered one and a malformed one are indistinguishable
     * from here, so the form cannot be used to find out who has an account.
     */
    @PostMapping("/request")
    ResponseEntity<Void> request(@Valid @RequestBody RequestPasswordResetRequest body) {
        requestPasswordReset.request(body.email());
        return ResponseEntity.noContent().build();
    }

    /** A link that is unknown, spent or expired answers 400, all three the same way. */
    @PostMapping
    ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest body) {
        resetPassword.reset(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
}
