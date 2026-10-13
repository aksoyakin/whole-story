package world.wholestory.ingest.collect;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import world.wholestory.ingest.config.IngestProperties;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
class EventController {

    private static final String REJECTED = "events.rejected";

    private final EventCollector collector;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meters;
    private final IngestProperties properties;

    /**
     * The tracker posts JSON as {@code text/plain} so that browsers skip the CORS preflight request.
     * <p>
     * The body is read here instead of being bound with {@code @RequestBody}, which is what lets it be bounded
     * at all: binding would have buffered the whole thing before this method was entered, and a size limit
     * applied after the read is not a limit (ADR 0016). Jackson is handed the bytes rather than a decoded
     * string for a second reason — JSON carries its own encoding, so nothing here has to guess a charset.
     */
    @PostMapping(path = "/api/event")
    ResponseEntity<Void> collect(@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                 @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
                                 HttpServletRequest request) throws IOException {
        byte[] body = BoundedBody.read(
                request.getInputStream(), request.getContentLengthLong(), properties.maxEventBytes());
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

    /**
     * Answered with the status that names the problem, rather than folding it into the 400 the other
     * validation failures share: whoever instrumented the site is the only one who can shorten the payload, and
     * a 413 tells them what to shorten without reading our source.
     */
    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<Void> payloadTooLarge() {
        return rejected("payload_too_large", HttpStatus.CONTENT_TOO_LARGE);
    }

    /** Every rejection is counted by reason, so a misconfigured site does not fail silently. */
    private ResponseEntity<Void> rejected(String reason, HttpStatus status) {
        meters.counter(REJECTED, "reason", reason).increment();
        return ResponseEntity.status(status).build();
    }
}
