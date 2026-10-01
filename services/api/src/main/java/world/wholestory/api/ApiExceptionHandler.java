package world.wholestory.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import world.wholestory.api.shared.domain.AlreadyExistsException;
import world.wholestory.api.shared.domain.DomainException;

/**
 * Turns domain and authentication failures into status codes. No message is returned: the status is all the web
 * app needs to choose its wording, and an error body is the easiest place to leak something by accident.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(AlreadyExistsException.class)
    ResponseEntity<Void> alreadyExists() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Void> brokenRule() {
        return ResponseEntity.badRequest().build();
    }

    /** Wrong password, unknown account and a malformed address all answer the same, on purpose. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Void> notAuthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
