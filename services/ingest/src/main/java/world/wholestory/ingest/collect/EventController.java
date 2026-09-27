package world.wholestory.ingest.collect;

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

    private final EventCollector collector;
    private final JsonMapper jsonMapper;

    /**
     * The tracker posts JSON as {@code text/plain} so that browsers skip the CORS preflight request.
     */
    @PostMapping(path = "/api/event")
    ResponseEntity<Void> collect(@RequestBody String body,
                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                 HttpServletRequest request) {
        IncomingEvent event = jsonMapper.readValue(body, IncomingEvent.class);
        collector.collect(event, request.getRemoteAddr(), userAgent);
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler({InvalidEventException.class, JacksonException.class})
    ResponseEntity<Void> badRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
