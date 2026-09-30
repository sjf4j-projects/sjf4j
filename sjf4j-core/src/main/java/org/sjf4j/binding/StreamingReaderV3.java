package org.sjf4j.binding;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Value-positioned pull reader design.
 *
 * <p>After {@link #startDocument()}, the cursor is on the root value. Value
 * readers and container begin/end methods do not advance it. The normal moving
 * methods, {@link #nextObjectName()}, {@link #nextObjectField(NameMatcher, int)},
 * and {@link #nextArrayElement()}, advance from a container start, completed scalar,
 * or completed child to the next value. On exhaustion they leave the matching
 * end token current. Containers entered explicitly with {@link #beginObject()} or
 * {@link #beginArray()} must be closed with {@link #endObject()} or
 * {@link #endArray()} before their parent is advanced. {@link #readRawNode()} and
 * {@link #skipValue()} self-complete a current container, so its parent can be
 * advanced directly when either leaves that container's end token current.
 * {@link #endDocument()} advances once from a completed root and requires EOF.</p>
 *
 * <p>Normal object traversal never exposes a field name: it leaves the field
 * value current. {@link OrderedFieldReaderV3} is the optional compiled-reader
 * extension that temporarily exposes FIELD_NAME for an ordered fast path.</p>
 */
public interface StreamingReaderV3 extends Closeable {

    enum Token {
        EOF,
        UNKNOWN,
        START_OBJECT,
        END_OBJECT,
        FIELD_NAME,
        START_ARRAY,
        END_ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    /** No field index is supplied as an ordered-name hint. */
    int NO_EXPECTED_FIELD = -1;

    /** The current object has no further fields and is at END_OBJECT. */
    int END_OF_OBJECT = -3;

    /** Positions the cursor on the one document root value. */
    void startDocument() throws IOException;

    /** Advances from the completed root and verifies EOF. */
    void endDocument() throws IOException;

    /** Returns the current token without advancing. */
    Token currentToken() throws IOException;

    /** Returns whether the current value is null without advancing. */
    default boolean isNull() throws IOException {
        return currentToken() == Token.NULL;
    }

    /**
     * Returns a cached backend matcher for a POJO type, or {@code null} when
     * the backend has no matching fast path.
     */
    default NameMatcher nameMatcher(Class<?> type) {
        return null;
    }

    /** Assumes the current value is START_OBJECT without advancing. */
    void beginObject() throws IOException;

    /**
     * Advances to the next object field value and returns its name. Returns
     * {@code null} with END_OBJECT current when exhausted.
     */
    String nextObjectName() throws IOException;

    /**
     * Advances to the next object field value and returns its matcher index or
     * {@link NameMatcher#UNKNOWN_FIELD}. Returns {@link #END_OF_OBJECT} with
     * END_OBJECT current when exhausted. Pass {@link #NO_EXPECTED_FIELD} when
     * no ordered-name hint is available.
     */
    int nextObjectField(NameMatcher matcher, int expectedIndex) throws IOException;

    /** Same as {@link #nextObjectField(NameMatcher, int)} without a hint. */
    default int nextObjectField(NameMatcher matcher) throws IOException {
        return nextObjectField(matcher, NO_EXPECTED_FIELD);
    }

    /**
     * Consumes a current FIELD_NAME left by a failed
     * {@link OrderedFieldReaderV3#nextExpectedName(NameMatcher, int)} call,
     * leaves its value current, and returns its matcher index or
     * {@link NameMatcher#UNKNOWN_FIELD}. If END_OBJECT is current, returns
     * {@link #END_OF_OBJECT}. This is only the ordered-path mismatch fallback.
     */
    int consumeCurrentObjectField(NameMatcher matcher) throws IOException;

    /** Assumes END_OBJECT is current without advancing. */
    void endObject() throws IOException;

    /** Assumes the current value is START_ARRAY without advancing. */
    void beginArray() throws IOException;

    /**
     * Advances to the next array value, or leaves END_ARRAY current and returns
     * false when exhausted.
     */
    boolean nextArrayElement() throws IOException;

    /** Assumes END_ARRAY is current without advancing. */
    void endArray() throws IOException;

    /** Reads the current string without advancing. */
    String readString() throws IOException;

    /** Reads the current string, or null for a current null token. */
    default String readStringOrNull() throws IOException {
        return isNull() ? null : readString();
    }

    /** Reads the current number without advancing. */
    Number readNumber() throws IOException;

    long readLongValue() throws IOException;

    int readIntValue() throws IOException;

    short readShortValue() throws IOException;

    byte readByteValue() throws IOException;

    double readDoubleValue() throws IOException;

    float readFloatValue() throws IOException;

    boolean readBooleanValue() throws IOException;

    char readCharValue() throws IOException;

    BigInteger readBigInteger() throws IOException;

    BigDecimal readBigDecimal() throws IOException;

    /** Validates a current null token without advancing. */
    void readNull() throws IOException;

    /**
     * Skips the current value. Scalars remain current; containers are self-completed
     * and leave their matching end token current, so their parent may be advanced
     * without calling {@link #endObject()} or {@link #endArray()}. The value must
     * not already have been entered.
     */
    void skipValue() throws IOException;

    /**
     * Materializes the current value as String, Number, Boolean, null,
     * LinkedHashMap, and ArrayList values without advancing past that value.
     * A container is self-completed and leaves its matching end token current, so
     * its parent may be advanced without calling {@link #endObject()} or
     * {@link #endArray()}.
     */
    Object readRawNode() throws IOException;
}
