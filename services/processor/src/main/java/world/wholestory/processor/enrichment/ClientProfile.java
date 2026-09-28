package world.wholestory.processor.enrichment;

/** Browser, operating system and device of a visitor, derived from the User-Agent. */
public record ClientProfile(String browser, String browserVersion, String os, String osVersion, String deviceType) {

    public static final ClientProfile UNKNOWN = new ClientProfile(null, null, null, null, null);
}
