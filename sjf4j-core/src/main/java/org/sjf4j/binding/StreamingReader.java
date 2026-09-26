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
     * --------------------------------------------------------------
     * Tokens
     * --------------------------------------------------------------
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
     * --------------------------------------------------------------
     * Property Matching
     * --------------------------------------------------------------
     */

    /**
     * Prepared backend-specific property-name matcher.
     *
     * <p>The index returned by {@link #match(String)} corresponds to the
     * canonical name returned by {@link #name(int)}.</p>
     */
    interface NameMatcher {

        int UNKNOWN = -1;

        /** Canonical property name for a matched index. */
        String name(int index);

        /** Generic String fallback. */
        int match(String name);
    }

    /**
     * Returns a cached matcher for the specified POJO type when this reader
     * has a backend-specific matching fast path, or {@code null} otherwise.
     *
     * <p>The default is deliberately {@code null}: backends such as Gson keep
     * the ordinary String-name path instead of paying for an extra matcher
     * layer.</p>
     */
    default NameMatcher nameMatcher(Class<?> type) {
        return null;
    }


    /*
     * --------------------------------------------------------------
     * Document
     * --------------------------------------------------------------
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
     * --------------------------------------------------------------
     * Token Inspection
     * --------------------------------------------------------------
     */

    /**
     * Returns the current token without consuming it.
     */
    Token peekToken() throws IOException;


    /*
     * --------------------------------------------------------------
     * Structural Fast Paths
     * --------------------------------------------------------------
     */

    boolean nextIfNull() throws IOException;

    boolean nextIfObjectEnd() throws IOException;

    boolean nextIfArrayEnd() throws IOException;


    /*
     * --------------------------------------------------------------
     * Structural Tokens
     * --------------------------------------------------------------
     */

    void startObject() throws IOException;

    void endObject() throws IOException;

    void startArray() throws IOException;

    void endArray() throws IOException;


    /*
     * --------------------------------------------------------------
     * Property Names
     * --------------------------------------------------------------
     */

    /**
     * Reads and consumes the next property name.
     *
     * <p>After this method returns, the reader is positioned so that
     * the corresponding property value can be consumed.</p>
     */
    String nextName() throws IOException;

    /**
     * Matches the next property name against prepared backend-specific
     * metadata. The default path materializes the String name.
     */
    default int nextNameMatch(NameMatcher matcher) throws IOException {
        return matcher.match(nextName());
    }

    /**
     * Same as {@link #nextNameMatch(NameMatcher)}, with an optional expected
     * property index hint for backends that can exploit ordered names.
     */
    default int nextNameMatch(
            NameMatcher matcher,
            int expectedIndex) throws IOException {

        return nextNameMatch(matcher);
    }


    /*
     * --------------------------------------------------------------
     * String
     * --------------------------------------------------------------
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
     * --------------------------------------------------------------
     * Generic Number
     * --------------------------------------------------------------
     */

    /**
     * Reads the current numeric value using the backend's natural
     * Number representation.
     */
    Number nextNumber() throws IOException;


    /*
     * --------------------------------------------------------------
     * Primitive Fast Paths
     * --------------------------------------------------------------
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
     * --------------------------------------------------------------
     * Boxed Compatibility APIs
     * --------------------------------------------------------------
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
     * --------------------------------------------------------------
     * Arbitrary Precision Numbers
     * --------------------------------------------------------------
     */

    BigInteger nextBigInteger() throws IOException;

    BigDecimal nextBigDecimal() throws IOException;


    /*
     * --------------------------------------------------------------
     * Null
     * --------------------------------------------------------------
     */

    void nextNull() throws IOException;


    /*
     * --------------------------------------------------------------
     * Skipping
     * --------------------------------------------------------------
     */

    /**
     * Consumes exactly one complete value at the current position.
     */
    void skipNext() throws IOException;
}
