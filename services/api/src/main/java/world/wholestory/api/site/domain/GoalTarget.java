package world.wholestory.api.site.domain;

import java.util.Objects;

/**
 * What a goal is looking for: an event name, or a path that may contain {@code *}.
 * <p>
 * One value object rather than two nullable fields, which is what the table's check constraint is about — a goal
 * carries an event name or a page pattern, never both and never neither. Expressed this way the invalid
 * combination cannot be built in the first place.
 * <p>
 * The rules here are all about goals that could never match anything. A goal that quietly counts zero for ever is
 * worse than one that is refused when it is defined, because nobody can tell the difference between "no
 * conversions yet" and "this was never going to work".
 */
public record GoalTarget(GoalType type, String value) {

    /** Ingest refuses an event name longer than this, so a longer goal could never match one. */
    public static final int MAX_EVENT_NAME_LENGTH = 120;
    public static final int MAX_PAGE_PATTERN_LENGTH = 1024;

    /** Matches any part of a path, including none. The only wildcard: one character, one rule. */
    public static final char WILDCARD = '*';

    public GoalTarget {
        Objects.requireNonNull(type, "a goal needs a type");
        value = value == null ? "" : value.trim();
        switch (type) {
            case EVENT -> requireEventName(value);
            case PAGEVIEW -> requirePagePattern(value);
        }
    }

    public static GoalTarget event(String name) {
        return new GoalTarget(GoalType.EVENT, name);
    }

    public static GoalTarget page(String pattern) {
        return new GoalTarget(GoalType.PAGEVIEW, pattern);
    }

    public boolean isPage() {
        return type == GoalType.PAGEVIEW;
    }

    private static void requireEventName(String name) {
        if (name.isEmpty()) {
            throw new InvalidGoalException("an event goal needs the event name the tracker reports");
        }
        if (name.length() > MAX_EVENT_NAME_LENGTH) {
            throw new InvalidGoalException("an event name is at most " + MAX_EVENT_NAME_LENGTH + " characters");
        }
    }

    /**
     * A path as the tracker records it: what follows the host, nothing else. The query string and the fragment
     * are refused rather than ignored, because {@code /thanks?ref=mail} looks reasonable and would match no
     * pageview ever — the path stored for it is {@code /thanks}.
     */
    private static void requirePagePattern(String pattern) {
        if (pattern.isEmpty() || pattern.charAt(0) != '/') {
            throw new InvalidGoalException("a page goal is a path beginning with '/', like /thanks or /blog/*");
        }
        if (pattern.length() > MAX_PAGE_PATTERN_LENGTH) {
            throw new InvalidGoalException("a path is at most " + MAX_PAGE_PATTERN_LENGTH + " characters");
        }
        if (pattern.indexOf('?') >= 0 || pattern.indexOf('#') >= 0) {
            throw new InvalidGoalException("a path holds no query string or fragment: those are not recorded");
        }
        for (int i = 0; i < pattern.length(); i++) {
            if (Character.isWhitespace(pattern.charAt(i))) {
                throw new InvalidGoalException("a path contains no spaces");
            }
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
