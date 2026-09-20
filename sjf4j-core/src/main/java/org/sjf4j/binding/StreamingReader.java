package org.sjf4j.binding;

import org.sjf4j.JsonType;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Unified streaming reader for structured data.
 *
 * <p>The interface defines common structural semantics while allowing
 * implementations to provide backend-specific fast paths.</p>
 *
 * <p>Generated binders should prefer primitive value methods and
 * {@link #nextNameMatch(NameMatcher)} where possible.</p>
 */
public interface StreamingReader extends Closeable {

    /*
     * ----------------------------------------------------------------------
     * Tokens
     * ----------------------------------------------------------------------
     */

    enum Token {

        EOF(0),
        UNKNOWN(1),

        START_OBJECT(2),
        END_OBJECT(3),
        NAME(4),

        START_ARRAY(5),
        END_ARRAY(6),

        STRING(7),
        NUMBER(8),
        BOOLEAN(9),
        NULL(10);

        private final int id;

        Token(int id) {
            this.id = id;
        }

        /**
         * Stable integer token id for hot-path dispatch.
         */
        public int id() {
            return id;
        }

        public JsonType jsonType() {
            switch (this) {
                case START_OBJECT:
                    return JsonType.OBJECT;
                case START_ARRAY:
                    return JsonType.ARRAY;
                case STRING:
                    return JsonType.STRING;
                case NUMBER:
                    return JsonType.NUMBER;
                case BOOLEAN:
                    return JsonType.BOOLEAN;
                case NULL:
                    return JsonType.NULL;
                default:
                    return JsonType.UNKNOWN;
            }
        }
    }


    /*
     * ----------------------------------------------------------------------
     * Property matching
     * ----------------------------------------------------------------------
     */

    /**
     * Prepared set of property names.
     *
     * <p>A matcher may contain backend-specific precomputed state:</p>
     *
     * <ul>
     *   <li>property hashes</li>
     *   <li>serialized UTF-8 names</li>
     *   <li>Jackson PropertyNameMatcher</li>
     *   <li>plain strings</li>
     * </ul>
     *
     * <p>Property indexes are stable and normally correspond to generated
     * property indexes.</p>
     */
    interface NameMatcher {

        /**
         * No known property matched.
         */
        int UNKNOWN = -1;

        /**
         * The next token is END_OBJECT.
         *
         * <p>The END_OBJECT token is not consumed by
         * {@code nextNameMatch}; the caller must call {@link #endObject()}.</p>
         */
        int END_OBJECT = -2;

        /**
         * Number of known properties.
         */
        int size();

        /**
         * Canonical property name for the specified index.
         */
        String name(int index);

        /**
         * Generic String-based fallback matching.
         *
         * @return property index or {@link #UNKNOWN}
         */
        int match(String name);
    }


    /*
     * ----------------------------------------------------------------------
     * Document
     * ----------------------------------------------------------------------
     */

    /**
     * Prepares this reader to consume one document without consuming
     * its root value.
     */
    default void startDocument() throws IOException {
    }

    /**
     * Completes a document after its root value has been consumed.
     */
    default void endDocument() throws IOException {
        if (peekToken() != Token.EOF) {
            throw new IOException("Expected end of document");
        }
    }


    /*
     * ----------------------------------------------------------------------
     * Token inspection
     * ----------------------------------------------------------------------
     */

    /**
     * Returns the current token without consuming it.
     */
    Token peekToken() throws IOException;


    /*
     * ----------------------------------------------------------------------
     * Structural fast paths
     * ----------------------------------------------------------------------
     */

    boolean nextIfNull() throws IOException;

    boolean nextIfObjectEnd() throws IOException;

    boolean nextIfArrayEnd() throws IOException;


    /*
     * ----------------------------------------------------------------------
     * Structural tokens
     * ----------------------------------------------------------------------
     */

    void startObject() throws IOException;

    void endObject() throws IOException;

    void startArray() throws IOException;

    void endArray() throws IOException;


    /*
     * ----------------------------------------------------------------------
     * Property names
     * ----------------------------------------------------------------------
     */

    /**
     * Reads and consumes the next property name.
     *
     * <p>After this method returns, the reader is positioned so that
     * the corresponding property value can be consumed.</p>
     */
    String nextName() throws IOException;

    /**
     * Matches the next property name against a prepared property set.
     *
     * <p>This is the primary fast-path API for generated binders.</p>
     *
     * <p>The default implementation falls back to String property-name
     * materialization. High-performance backends should override this
     * method.</p>
     *
     * @return matched property index,
     *         {@link NameMatcher#UNKNOWN}, or
     *         {@link NameMatcher#END_OBJECT}
     */
    default int nextNameMatch(
            NameMatcher matcher) throws IOException {

        if (peekToken() == Token.END_OBJECT) {
            return NameMatcher.END_OBJECT;
        }

        return matcher.match(nextName());
    }

    /**
     * Matches the next property name, with an optional expected property index.
     *
     * <p>{@code expectedIndex} is only a performance hint. Implementations
     * must remain correct when properties are reordered, omitted, or unknown.</p>
     *
     * <p>This allows implementations such as an ordered-name reader to
     * attempt an expected-name fast path first, and fall back to general
     * matching on a miss.</p>
     *
     * <p>A negative expected index means that no ordered-name hint is
     * available.</p>
     */
    default int nextNameMatch(
            NameMatcher matcher,
            int expectedIndex) throws IOException {

        return nextNameMatch(matcher);
    }


    /*
     * ----------------------------------------------------------------------
     * String
     * ----------------------------------------------------------------------
     */

    /**
     * Reads the current value as a String.
     *
     * <p>The current token must represent a string value.</p>
     */
    String nextString() throws IOException;

    /**
     * Reads a nullable String.
     *
     * <p>Implementations may override this to provide a fused null/string
     * fast path.</p>
     */
    default String nextStringOrNull() throws IOException {
        if (nextIfNull()) {
            return null;
        }

        return nextString();
    }


    /*
     * ----------------------------------------------------------------------
     * Generic number
     * ----------------------------------------------------------------------
     */

    /**
     * Reads the current numeric value using the backend's natural
     * Number representation.
     */
    Number nextNumber() throws IOException;


    /*
     * ----------------------------------------------------------------------
     * Primitive fast paths
     * ----------------------------------------------------------------------
     */

    long nextLongValue() throws IOException;

    int nextIntValue() throws IOException;

    short nextShortValue() throws IOException;

    byte nextByteValue() throws IOException;

    double nextDoubleValue() throws IOException;

    float nextFloatValue() throws IOException;

    boolean nextBooleanValue() throws IOException;

    char nextCharValue() throws IOException;

    /*
     * ----------------------------------------------------------------------
     * Boxed compatibility APIs
     * ----------------------------------------------------------------------
     */

    default Long nextLong() throws IOException {
        return nextIfNull() ? null : nextLongValue();
    }

    default Integer nextInt() throws IOException {
        return nextIfNull() ? null : nextIntValue();
    }

    default Short nextShort() throws IOException {
        return nextIfNull() ? null : nextShortValue();
    }

    default Byte nextByte() throws IOException {
        return nextIfNull() ? null : nextByteValue();
    }

    default Double nextDouble() throws IOException {
        return nextIfNull() ? null : nextDoubleValue();
    }

    default Float nextFloat() throws IOException {
        return nextIfNull() ? null : nextFloatValue();
    }

    default Boolean nextBoolean() throws IOException {
        return nextIfNull() ? null : nextBooleanValue();
    }


    /*
     * ----------------------------------------------------------------------
     * Arbitrary precision numbers
     * ----------------------------------------------------------------------
     */

    BigInteger nextBigInteger() throws IOException;

    BigDecimal nextBigDecimal() throws IOException;


    /*
     * ----------------------------------------------------------------------
     * Null
     * ----------------------------------------------------------------------
     */

    void nextNull() throws IOException;


    /*
     * ----------------------------------------------------------------------
     * Skipping
     * ----------------------------------------------------------------------
     */

    /**
     * Consumes exactly one complete value at the current position.
     */
    void skipNext() throws IOException;
}
