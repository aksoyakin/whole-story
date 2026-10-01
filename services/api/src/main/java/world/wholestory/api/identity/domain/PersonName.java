package world.wholestory.api.identity.domain;

/** A display name. Trimmed, never empty, bounded so that it cannot be used to push a megabyte into a column. */
public record PersonName(String value) {

    private static final int MAX_LENGTH = 120;

    public PersonName {
        value = value == null ? "" : value.trim();
        if (value.isEmpty()) {
            throw new InvalidNameException("a name is required");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidNameException("a name may be at most " + MAX_LENGTH + " characters");
        }
    }

    public static PersonName of(String value) {
        return new PersonName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
