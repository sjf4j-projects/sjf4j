package org.sjf4j.util;


/**
 * Shared assertion helpers.
 */
public final class Asserts {

    private Asserts() {
    }

    /**
     * Returns the value when non-null, otherwise throws a {@link NullPointerException}.
     */
    public static <T> T notNull(T value, String name) {
        if (value == null) throw new NullPointerException("'" + name + "' must not be null");
        return value;
    }

}
