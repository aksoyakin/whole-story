package world.wholestory.ingest.privacy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Cookieless visitor identity: {@code sha256(dailySalt + siteId + ip + userAgent)} truncated to 64 bits (D-035).
 * Stable within a UTC day, unlinkable across days.
 */
@Component
@RequiredArgsConstructor
public class VisitorHasher {

    private static final byte SEPARATOR = 0;

    private final DailySaltProvider saltProvider;

    public long hash(Instant at, UUID siteId, String ipAddress, String userAgent) {
        byte[] salt = saltProvider.saltFor(at.atZone(ZoneOffset.UTC).toLocalDate());
        return hash(salt, siteId, ipAddress, userAgent);
    }

    static long hash(byte[] salt, UUID siteId, String ipAddress, String userAgent) {
        MessageDigest sha256 = sha256();
        sha256.update(salt);
        sha256.update(SEPARATOR);
        sha256.update(siteId.toString().getBytes(StandardCharsets.UTF_8));
        sha256.update(SEPARATOR);
        sha256.update(ipAddress.getBytes(StandardCharsets.UTF_8));
        sha256.update(SEPARATOR);
        sha256.update(userAgent.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.wrap(sha256.digest()).getLong();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK", e);
        }
    }
}
