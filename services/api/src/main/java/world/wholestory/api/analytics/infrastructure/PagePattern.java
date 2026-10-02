package world.wholestory.api.analytics.infrastructure;

/**
 * Turns a page goal's pattern into a SQL {@code like} pattern.
 * <p>
 * The product's wildcard is {@code *} and it means "any part of a path". SQL's own wildcards, {@code %} and
 * {@code _}, have to be escaped, or a path containing one would match far more than it says: {@code /100%-free}
 * would otherwise match every path beginning with {@code /100}. The backslash is escaped for the same reason,
 * since it is the escape character itself.
 * <p>
 * The result is bound as a parameter, never spliced into the statement.
 */
final class PagePattern {

    /** Declared in the statement with {@code escape '\'}, so this is the character the database expects. */
    static final char ESCAPE = '\\';

    private PagePattern() {
    }

    static String toLikePattern(String pattern) {
        StringBuilder like = new StringBuilder(pattern.length() + 8);
        for (int i = 0; i < pattern.length(); i++) {
            char character = pattern.charAt(i);
            switch (character) {
                case '*' -> like.append('%');
                case '%', '_', ESCAPE -> like.append(ESCAPE).append(character);
                default -> like.append(character);
            }
        }
        return like.toString();
    }
}
