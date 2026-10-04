package org.sjf4j.binding;
import org.sjf4j.JsonType;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Streaming reader for JSON-semantic structured data.
 *
 * <p>The reader operates on a logical stream of object, array, and value
 * tokens. Backend implementations may use different physical parser states
 * internally as long as they preserve the semantics defined by this
 * interface.</p>
 *
 * <p>Property-name traversal and value consumption are deliberately
 * separated. For example:</p>
 *
 * <pre>{@code
 * reader.startObject();
 *
 * int index;
 * while ((index = reader.nextNameMatch(matcher)) != NameMatcher.END_OF_OBJECT) {
 *
 *     switch (index) {
 *         case 0:
 *             bean.id = reader.readIntValue();
 *             break;
 *         case 1:
 *             bean.name = reader.readString();
 *             break;
 *         default:
 *             reader.skipValue();
 *     }
 * }
 * }</pre>
 *
 * <p>{@link #nextName()} and {@link #nextNameMatch(NameMatcher)} consume
 * a property name but do not consume its value. The corresponding value
 * remains pending until consumed by a {@code readXxx()} method,
 * {@link #skipNode()}, or another value-consuming operation.</p>
 */
public interface StreamingReaderV4 extends Closeable {

    /**
     * --------------------------------------------------------------
     * Tokens
     * --------------------------------------------------------------
     */

    /**
     * Logical token types exposed by the streaming reader.
     *
     * <p>These tokens describe the SJF4J reader state rather than the
     * physical token state of a backend parser.</p>
     */
    enum Token {

        EOF,
        UNKNOWN,

        START_OBJECT,
        END_OBJECT,
        NAME,

        START_ARRAY,
        END_ARRAY,

        STRING,
        NUMBER,
        BOOLEAN,
        NULL;

        /**
         * Returns the JSON-semantic type represented by this token.
         *
         * <p>Tokens that do not themselves represent a JSON value, such as
         * {@link #NAME}, {@link #END_OBJECT}, and {@link #END_ARRAY}, map to
         * {@link JsonType#UNKNOWN}.</p>
         */
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


    /**
     * --------------------------------------------------------------
     * Property Matching
     * --------------------------------------------------------------
     */

    /**
     * Prepared property-name matcher.
     *
     * <p>A successful match returns a non-negative index. The canonical
     * property name for that index can be obtained through
     * {@link #name(int)}.</p>
     *
     * <p>Backend implementations may attach native matching metadata to an
     * implementation of this interface. This allows
     * {@link #nextNameMatch(NameMatcher)} to match directly against the
     * underlying input without first materializing the property name as a
     * {@link String}.</p>
     */
    interface NameMatcher {

        /**
         * Indicates that a property name did not match any known name.
         */
        int UNKNOWN = -1;

        /**
         * Indicates that object traversal reached the object end.
         */
        int END_OF_OBJECT = -2;

        /**
         * Returns the canonical property name associated with a matched
         * index.
         */
        String name(int index);

        /**
         * Matches an already materialized property name.
         *
         * <p>This method is also the generic fallback used by backends that
         * do not provide a native property-name matching fast path.</p>
         *
         * @param name property name
         * @return a non-negative property index, or {@link #UNKNOWN}
         */
        int match(String name);
    }

    /**
     * Returns a prepared matcher for the specified type when this backend
     * provides a specialized property-name matching path.
     *
     * <p>The returned matcher may contain backend-specific metadata and may
     * be cached by the implementation.</p>
     *
     * <p>The default implementation returns {@code null}. Backends without
     * a specialized matching facility may use the ordinary
     * {@link #nextName()} path instead.</p>
     *
     * @param type target object type
     * @return prepared matcher, or {@code null} when unavailable
     */
    default NameMatcher nameMatcher(Class<?> type) {
        return null;
    }


    /**
     * --------------------------------------------------------------
     * Document
     * --------------------------------------------------------------
     */

    /**
     * Prepares this reader to consume one document.
     *
     * <p>The root value is not consumed.</p>
     */
    default void startDocument() throws IOException {
    }

    /**
     * Completes the current document after its root value has been consumed.
     *
     * <p>The default implementation verifies that the logical input has
     * reached the end of the document.</p>
     */
    default void endDocument() throws IOException {
        if (peekToken() != Token.EOF) {
            throw new IOException("expected end of document");
        }
    }


    /**
     * --------------------------------------------------------------
     * Token Inspection
     * --------------------------------------------------------------
     */

    /**
     * Returns the next logical token without consuming it.
     *
     * <p>This method exposes the logical state of this reader, not
     * necessarily the current physical token of the underlying parser.</p>
     *
     * <p>It is primarily intended for dynamic or runtime dispatch.
     * Generated binding code should normally prefer specialized operations
     * such as {@link #readIntValue()}, {@link #readString()}, and
     * {@link #nextNameMatch(NameMatcher)} so that backend-specific fast
     * paths remain available.</p>
     */
    Token peekToken() throws IOException;


    /**
     * --------------------------------------------------------------
     * Conditional Structural Operations
     * --------------------------------------------------------------
     */

    /**
     * Consumes a {@code null} value when it is next.
     *
     * <p>If the next logical value is not {@code null}, this method returns
     * {@code false} without consuming input.</p>
     *
     * @return {@code true} if {@code null} was consumed
     */
    boolean nextIfNull() throws IOException;

    /**
     * Consumes an object start when it is next.
     *
     * <p>If the next logical token is not an object start, this method
     * returns {@code false} without consuming input.</p>
     *
     * @return {@code true} if an object start was consumed
     */
    boolean nextIfObjectStart() throws IOException;

    /**
     * Consumes the end of the current object when it is next.
     *
     * <p>If another property follows, this method returns {@code false}
     * without consuming the property name.</p>
     *
     * @return {@code true} if the object end was consumed
     */
    boolean nextIfObjectEnd() throws IOException;

    /**
     * Consumes an array start when it is next.
     *
     * <p>If the next logical token is not an array start, this method
     * returns {@code false} without consuming input.</p>
     *
     * @return {@code true} if an array start was consumed
     */
    boolean nextIfArrayStart() throws IOException;

    /**
     * Consumes the end of the current array when it is next.
     *
     * <p>If another array element follows, this method returns {@code false}
     * without consuming that element.</p>
     *
     * @return {@code true} if the array end was consumed
     */
    boolean nextIfArrayEnd() throws IOException;


    /**
     * --------------------------------------------------------------
     * Structural Operations
     * --------------------------------------------------------------
     */

    /**
     * Consumes the start of an object.
     *
     * @throws IOException if the next logical token is not an object start
     */
    void startObject() throws IOException;

    /**
     * Consumes the end of the current object.
     *
     * @throws IOException if the next logical token is not an object end
     */
    void endObject() throws IOException;

    /**
     * Consumes the start of an array.
     *
     * @throws IOException if the next logical token is not an array start
     */
    void startArray() throws IOException;

    /**
     * Consumes the end of the current array.
     *
     * @throws IOException if the next logical token is not an array end
     */
    void endArray() throws IOException;


    /**
     * --------------------------------------------------------------
     * Property Names
     * --------------------------------------------------------------
     */

    /**
     * Consumes and returns the next property name.
     *
     * <p>If the current object has ended, the object end is consumed and
     * {@code null} is returned.</p>
     *
     * <p>When a property name is returned, its corresponding value remains
     * pending and must subsequently be consumed.</p>
     *
     * @return the next property name, or {@code null} when the current object ends
     */
    String nextName() throws IOException;

    /**
     * Advances to the next property name and matches it against prepared
     * property metadata.
     *
     * <p>If another property is present, its name is consumed and its
     * corresponding value remains pending. The value must subsequently be
     * consumed by a {@code readXxx()} method, {@link #skipNode()}, or another
     * value-consuming operation.</p>
     *
     * <p>If the enclosing object ends instead, the object end is consumed and
     * {@link NameMatcher#END_OF_OBJECT} is returned.</p>
     *
     * <p>This is the preferred object-traversal operation for generated binding
     * code. Backends with native property-name matching should override this
     * method and match directly against the underlying input without
     * materializing the property name as a {@link String} whenever possible.</p>
     *
     * @param matcher prepared property-name matcher
     * @return a non-negative property index,
     *         {@link NameMatcher#UNKNOWN}, or
     *         {@link NameMatcher#END_OF_OBJECT}
     */
    default int nextNameMatch(NameMatcher matcher) throws IOException {
        String name = nextName();
        return name == null ? NameMatcher.END_OF_OBJECT : matcher.match(name);
    }

    /**
     * Consumes and matches the next property name, with an expected property
     * index hint.
     *
     * <p>The hint allows implementations to optimize the common case where
     * properties occur in a predictable order. It does not affect matching
     * semantics and callers must not depend on the hint being honored.</p>
     *
     * <p>The corresponding property value remains pending exactly as with
     * {@link #nextNameMatch(NameMatcher)}.</p>
     *
     * @param matcher prepared property-name matcher
     * @param expectedIndex expected property index
     * @return a non-negative property index, or
     *         {@link NameMatcher#UNKNOWN}
     */
    default int nextNameMatch(
            NameMatcher matcher,
            int expectedIndex) throws IOException {

        return nextNameMatch(matcher);
    }


    /**
     * --------------------------------------------------------------
     * String
     * --------------------------------------------------------------
     */

    /**
     * Reads and consumes the current logical value as a {@link String}.
     *
     * <p>Returns {@code null} when the current logical value is JSON
     * {@code null}.</p>
     *
     * @return string value, or {@code null}
     */
    String readString() throws IOException;


    /**
     * --------------------------------------------------------------
     * Generic Number
     * --------------------------------------------------------------
     */

    /**
     * Reads and consumes the current logical numeric value using the
     * backend's natural {@link Number} representation.
     *
     * <p>Returns {@code null} when the current logical value is JSON
     * {@code null}.</p>
     *
     * @return numeric value, or {@code null}
     */
    Number readNumber() throws IOException;


    /**
     * --------------------------------------------------------------
     * Primitive Values
     * --------------------------------------------------------------
     */

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code long}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    long readLongValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code int}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    int readIntValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code short}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    short readShortValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code byte}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    byte readByteValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code double}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    double readDoubleValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code float}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    float readFloatValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code boolean}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    boolean readBooleanValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code char}.
     *
     * <p>JSON {@code null} is not accepted. The accepted string
     * representation follows SJF4J binding semantics.</p>
     */
    char readCharValue() throws IOException;


    /**
     * --------------------------------------------------------------
     * Boxed Primitive Values
     * --------------------------------------------------------------
     */

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Long}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Long readLong() throws IOException {
        return nextIfNull() ? null : readLongValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Integer}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Integer readInt() throws IOException {
        return nextIfNull() ? null : readIntValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Short}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Short readShort() throws IOException {
        return nextIfNull() ? null : readShortValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Byte}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Byte readByte() throws IOException {
        return nextIfNull() ? null : readByteValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Double}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Double readDouble() throws IOException {
        return nextIfNull() ? null : readDoubleValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Float}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Float readFloat() throws IOException {
        return nextIfNull() ? null : readFloatValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Boolean}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Boolean readBoolean() throws IOException {
        return nextIfNull() ? null : readBooleanValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Character}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    default Character readChar() throws IOException {
        return nextIfNull() ? null : readCharValue();
    }


    /**
     * --------------------------------------------------------------
     * Arbitrary-Precision Numbers
     * --------------------------------------------------------------
     */

    /**
     * Reads and consumes the current logical numeric value as a
     * {@link BigInteger}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    BigInteger readBigInteger() throws IOException;

    /**
     * Reads and consumes the current logical numeric value as a
     * {@link BigDecimal}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    BigDecimal readBigDecimal() throws IOException;


    /**
     * --------------------------------------------------------------
     * Null
     * --------------------------------------------------------------
     */

    /**
     * Consumes a JSON {@code null}.
     *
     * @throws IOException if the current logical value is not {@code null}
     */
    void readNull() throws IOException;


    /**
     * --------------------------------------------------------------
     * Skipping
     * --------------------------------------------------------------
     */

    /**
     * Consumes exactly one complete logical value.
     *
     * <p>The value may be a scalar, object, or array. When called after
     * {@link #nextName()} or {@link #nextNameMatch(NameMatcher)}, this
     * method consumes the pending value of that property.</p>
     */
    void skipNode() throws IOException;


    /**
     * --------------------------------------------------------------
     * Generic Node
     * --------------------------------------------------------------
     */

    /**
     * Reads and consumes one complete value as its natural raw OBNT node.
     *
     * <p>This is primarily a dynamic fallback operation. Statically known
     * binding code should prefer the specialized typed methods whenever
     * possible.</p>
     */
    default Object readRawNode() throws IOException {
        return StreamingIO.readRawNode(this);
    }
}
