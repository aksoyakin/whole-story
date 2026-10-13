package world.wholestory.ingest.collect;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The size limit on an event, tested where it has to hold: against a caller who is not telling the truth about
 * how much it is about to send.
 */
class BoundedBodyTest {

    private static final int LIMIT = 16;

    @Test
    void refusesABodyThatAnnouncesItselfTooLarge() {
        InputStream neverRead = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("a body declared too large must be refused before it is read");
            }
        };

        assertThatThrownBy(() -> BoundedBody.read(neverRead, LIMIT + 1, LIMIT))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void refusesALongBodyThatDeclaredNoLengthAtAll() throws IOException {
        // What a chunked request looks like here, and the reason the declared length cannot be the only check.
        assertThatThrownBy(() -> BoundedBody.read(bytes(LIMIT * 100), -1, LIMIT))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void refusesALongBodyThatUnderstatedItsLength() {
        assertThatThrownBy(() -> BoundedBody.read(bytes(LIMIT * 100), 1, LIMIT))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void readsABodyThatExactlyFitsTheLimit() throws IOException {
        byte[] read = BoundedBody.read(bytes(LIMIT), LIMIT, LIMIT);

        // The limit is the largest body accepted, not the first one refused.
        assertThat(read).hasSize(LIMIT);
    }

    @Test
    void readsABodyWhole() throws IOException {
        String body = """
                {"name":"pageview"}""";

        byte[] read = BoundedBody.read(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), body.length(), 1024);

        assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo(body);
    }

    @Test
    void readsAnEmptyBodyWithoutComplaining() throws IOException {
        // Nothing to do with size: an empty body is a malformed payload, and saying so is the parser's job.
        assertThat(BoundedBody.read(bytes(0), 0, LIMIT)).isEmpty();
    }

    private static InputStream bytes(int count) {
        return new ByteArrayInputStream(new byte[count]);
    }
}
