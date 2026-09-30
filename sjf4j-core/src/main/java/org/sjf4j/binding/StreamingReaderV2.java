package org.sjf4j.binding;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Design sketch for a pull-cursor reader protocol.
 *
 * <p>This interface is intentionally independent of {@link StreamingReader}.
 * After {@link #startDocument()}, the cursor is positioned on the root token.
 * Token inspection, scalar reads, and container begin/end calls never advance
 * the parser. A scalar therefore remains current after it is read, and a
 * container remains current at its end token after {@code endObject()} or
 * {@code endArray()}.</p>
 *
 * <p>{@link #nextObjectField()} and {@link #nextArrayElement()} are the normal
 * cursor-moving operations. They advance from a container start, a completed
 * scalar, or the end of a completed child to the next value. At an exhausted
 * container they leave its end token current. A caller must close a completed
 * child before advancing its parent. {@link #endDocument()} advances exactly
 * once from the completed root and requires EOF.</p>
 */
public interface StreamingReaderV2 extends Closeable {

    /** Tokens visible at V2 cursor boundaries. FIELD_NAME is never exposed. */
    enum Token {
        EOF,
        UNKNOWN,
        START_OBJECT,
        END_OBJECT,
        START_ARRAY,
        END_ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    /** Prepared property-name matcher for generic fallback dispatch. */
    interface NameMatcher {
        int UNKNOWN = -1;

        String name(int index);

        int match(String name);
    }

    /** No field index is supplied as an ordered-name hint. */
    int NO_EXPECTED_FIELD = -1;

    /** The current object field was not known by the supplied matcher. */
    int UNKNOWN_FIELD = -1;

    /** The current object has no further fields and is positioned at END_OBJECT. */
    int END_OF_OBJECT = -2;

    /** Positions the cursor on the single document root value. */
    void startDocument() throws IOException;

    /**
     * Advances once from the completed root value and verifies that no token
     * follows it.
     */
    void endDocument() throws IOException;

    /** Returns the current token without advancing. */
    Token currentToken() throws IOException;

    /** Returns whether the current token is JSON null without advancing. */
    default boolean isNull() throws IOException {
        return currentToken() == Token.NULL;
    }

    /** Validates that the current token starts an object without advancing. */
    void beginObject() throws IOException;

    /**
     * Advances to the next object field value and returns its name.
     * Returns {@code null} without advancing when the current object is at
     * END_OBJECT.
     */
    String nextObjectField() throws IOException;

    /**
     * Advances to the next object field value and returns its matcher index,
     * {@link #UNKNOWN_FIELD}, or {@link #END_OF_OBJECT}.
     */
    int nextObjectField(NameMatcher matcher) throws IOException;

    /**
     * Same as {@link #nextObjectField(NameMatcher)}, with an
     * optional ordered-field hint. Pass {@link #NO_EXPECTED_FIELD} when no
     * hint is available.
     */
    int nextObjectField(NameMatcher matcher, int expectedIndex)
            throws IOException;

    /** Validates the current END_OBJECT token without advancing. */
    void endObject() throws IOException;

    /** Validates that the current token starts an array without advancing. */
    void beginArray() throws IOException;

    /**
     * Advances to the next array element value. Returns {@code false} without
     * advancing when the current array is at END_ARRAY.
     */
    boolean nextArrayElement() throws IOException;

    /** Validates the current END_ARRAY token without advancing. */
    void endArray() throws IOException;

    /** Reads the current string token without advancing. */
    String readString() throws IOException;

    /** Reads the current string token, or returns null for a current null token. */
    default String readStringOrNull() throws IOException {
        return isNull() ? null : readString();
    }

    /** Reads the current number token without advancing. */
    Number readNumber() throws IOException;

    /** Reads the current number token, or returns null for a current null token. */
    default Number readNumberOrNull() throws IOException {
        return isNull() ? null : readNumber();
    }

    long readLongValue() throws IOException;

    int readIntValue() throws IOException;

    short readShortValue() throws IOException;

    byte readByteValue() throws IOException;

    double readDoubleValue() throws IOException;

    float readFloatValue() throws IOException;

    boolean readBooleanValue() throws IOException;

    char readCharValue() throws IOException;

    default Long readLongOrNull() throws IOException {
        return isNull() ? null : readLongValue();
    }

    default Integer readIntOrNull() throws IOException {
        return isNull() ? null : readIntValue();
    }

    default Short readShortOrNull() throws IOException {
        return isNull() ? null : readShortValue();
    }

    default Byte readByteOrNull() throws IOException {
        return isNull() ? null : readByteValue();
    }

    default Double readDoubleOrNull() throws IOException {
        return isNull() ? null : readDoubleValue();
    }

    default Float readFloatOrNull() throws IOException {
        return isNull() ? null : readFloatValue();
    }

    default Boolean readBooleanOrNull() throws IOException {
        return isNull() ? null : readBooleanValue();
    }

    default Character readCharOrNull() throws IOException {
        return isNull() ? null : readCharValue();
    }

    BigInteger readBigInteger() throws IOException;

    default BigInteger readBigIntegerOrNull() throws IOException {
        return isNull() ? null : readBigInteger();
    }

    BigDecimal readBigDecimal() throws IOException;

    default BigDecimal readBigDecimalOrNull() throws IOException {
        return isNull() ? null : readBigDecimal();
    }

    /** Validates the current null token without advancing. */
    void readNull() throws IOException;

    /**
     * Skips the current value. A scalar remains current; a container is
     * consumed through its matching end token, which remains current.
     * The value must not already have been entered with beginObject/beginArray.
     */
    void skipValue() throws IOException;

    /**
     * Recursively materializes the current value as String, Number, Boolean,
     * null, LinkedHashMap, and ArrayList values. It leaves the cursor at the
     * scalar or matching end token of that value.
     */
    Object readRawNode() throws IOException;
}
