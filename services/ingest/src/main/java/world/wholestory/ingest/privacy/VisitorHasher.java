package world.wholestory.ingest.privacy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
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

    private final DailySalts salts;

    /**
     * For the first 30 minutes of a UTC day the visitor is also hashed with the previous day's salt, so that the
     * processor can continue a session that started before midnight instead of splitting it in two (D-028).
     */
    public VisitorIdentity identify(Instant at, UUID siteId, String ipAddress, String userAgent) {
        LocalDate today = at.atZone(ZoneOffset.UTC).toLocalDate();
        long hash = hash(salts.saltFor(today), siteId, ipAddress, userAgent);
        if (!isWithinRotationGrace(at, today)) {
            return new VisitorIdentity(hash, null);
        }
        Long previousHash = salts.existingSaltFor(today.minusDays(1))
                .map(salt -> hash(salt, siteId, ipAddress, userAgent))
                .orElse(null);
        return new VisitorIdentity(hash, previousHash);
    }

    private static boolean isWithinRotationGrace(Instant at, LocalDate today) {
        Instant midnight = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        return at.isBefore(midnight.plus(DailySaltProvider.ROTATION_GRACE));
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
