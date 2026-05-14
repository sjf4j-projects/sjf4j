package org.sjf4j.node;

/**
 * Optional interface for {@link ValueCodec}s that support date/time format patterns.
 * <p>
 * A codec implementing this interface can be parameterized with a
 * {@link java.time.format.DateTimeFormatter} pattern (e.g. {@code "yyyy-MM-dd"})
 * via the {@link #withPattern(String)} method. The returned codec instance applies
 * the pattern during {@link ValueCodec#valueToRaw(Object)} encoding and
 * {@link ValueCodec#rawToValue(Object)} decoding.
 * <p>
 * Implementations must be deterministic: repeated calls with the same pattern
 * must return behaviorally equivalent (though possibly different) instances.
 */
public interface PatternedValueCodec<V, R> extends ValueCodec<V, R> {

    /**
     * Returns a new {@link ValueCodec} configured with the given format pattern.
     * <p>
     * The pattern follows {@link java.time.format.DateTimeFormatter} conventions.
     *
     * @param pattern the format pattern, never null and must be a valid pattern
     * @return a codec instance using the given pattern
     */
    ValueCodec<V, R> withPattern(String pattern);
}
