package world.wholestory.ingest.collect;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a request body only as far as ingest is willing to hold one.
 * <p>
 * Its own class so that the guarantee can be tested for what it is: a limit that holds when the caller is not
 * cooperating. Two checks, and the second is the one that makes it a limit.
 */
final class BoundedBody {

    private BoundedBody() {
    }

    /**
     * @param declaredLength {@code Content-Length}, or a negative number when the request did not state one
     * @return the body, at most {@code limit} bytes of it
     * @throws PayloadTooLargeException if the body is, or claims to be, longer than {@code limit}
     */
    static byte[] read(InputStream body, long declaredLength, int limit) throws IOException {
        // The cheap rejection: a body that announces itself as too large costs nothing but its headers.
        if (declaredLength > limit) {
            throw new PayloadTooLargeException();
        }
        // On its own the check above is not a limit at all. A chunked request declares no length, and a caller
        // is free to understate the one it does declare, so the read is capped as well — at one byte past the
        // limit, which is what tells a body that is too long from one that exactly fits.
        byte[] bytes = body.readNBytes(limit + 1);
        if (bytes.length > limit) {
            throw new PayloadTooLargeException();
        }
        return bytes;
    }
}
