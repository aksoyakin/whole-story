package world.wholestory.ingest.privacy;

/**
 * How a visitor is recognised today, and how they were recognised yesterday when the salt has just rotated.
 *
 * @param hash         identity under the current day's salt
 * @param previousHash identity under the previous day's salt, only during the rotation grace period, else null
 */
public record VisitorIdentity(long hash, Long previousHash) {
}
