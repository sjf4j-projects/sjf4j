package org.sjf4j.binding;

import java.io.IOException;

/**
 * Optional compiled-reader extension for an unrolled ordered object path.
 *
 * <p>The {@code next...} methods consume a FIELD_NAME, unlike the base
 * value-positioned {@code read...} methods. Direct scalar consumers use the
 * backend's fused/default/coercion behavior where available; generated readers
 * must not treat that behavior as equivalent to strict base reads.</p>
 */
public interface OrderedFieldReaderV3 extends StreamingReaderV3 {

    /**
     * Advances to the next FIELD_NAME and tests it against {@code expectedIndex}.
     * On either result FIELD_NAME or END_OBJECT remains current.
     */
    boolean nextExpectedName(NameMatcher matcher, int expectedIndex) throws IOException;

    /** Advances from a current FIELD_NAME to its value. */
    void nextValue() throws IOException;

    /** Consumes FIELD_NAME and reads its long value with backend fused semantics. */
    long nextLongValue() throws IOException;

    /** Consumes FIELD_NAME and reads its int value with backend fused semantics. */
    int nextIntValue() throws IOException;

    /** Consumes FIELD_NAME and reads its boolean value with backend fused semantics. */
    boolean nextBooleanValue() throws IOException;

    /** Consumes FIELD_NAME and reads its double value with backend direct semantics. */
    double nextDoubleValue() throws IOException;

    /** Consumes FIELD_NAME and reads its string or null with backend fused semantics. */
    String nextStringOrNull() throws IOException;
}
