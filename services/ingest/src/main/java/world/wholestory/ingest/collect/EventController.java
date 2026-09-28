package world.wholestory.ingest.collect;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequiredArgsConstructor
class EventController {

    private static final String REJECTED = "events.rejected";

    private final EventCollector collector;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meters;

    /**
     * The tracker posts JSON as {@code text/plain} so that browsers skip the CORS preflight request.
     */
    @PostMapping(path = "/api/event")
    ResponseEntity<Void> collect(@RequestBody String body,
                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                 @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
                                 HttpServletRequest request) {
        IncomingEvent event = jsonMapper.readValue(body, IncomingEvent.class);
        collector.collect(event, request.getRemoteAddr(), userAgent, origin);
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(InvalidEventException.class)
    ResponseEntity<Void> invalidEvent(InvalidEventException e) {
        return rejected(e.getReason(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(JacksonException.class)
    ResponseEntity<Void> malformedPayload() {
        return rejected("malformed_payload", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Void> rateLimited() {
        return rejected("rate_limited", HttpStatus.TOO_MANY_REQUESTS);
    }

    /** Every rejection is counted by reason, so a misconfigured site does not fail silently. */
    private ResponseEntity<Void> rejected(String reason, HttpStatus status) {
        meters.counter(REJECTED, "reason", reason).increment();
        return ResponseEntity.status(status).build();
    }
}
