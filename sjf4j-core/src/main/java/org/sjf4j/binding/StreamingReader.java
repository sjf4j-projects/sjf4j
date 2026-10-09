package org.sjf4j.binding;
import org.sjf4j.JsonType;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.PropertyInfo;

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
 * <p>Member-name traversal and value consumption are deliberately
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
 * a member name but do not consume its value. The corresponding value
 * remains pending until consumed by a {@code readXxx()} method,
 * {@link #skipNode()}, or another value-consuming operation.</p>
 */
public abstract class StreamingReader implements Closeable {

    final Backend backend;

    public StreamingReader(Backend backend) {
        this.backend = backend;
    }

    /**
     * Logical token types exposed by the streaming reader.
     *
     * <p>These tokens describe the SJF4J reader state rather than the
     * physical token state of a backend parser.</p>
     */
    public enum Token {
        EOF,
        UNKNOWN,

        OBJECT_START,
        OBJECT_END,
        NAME,

        ARRAY_START,
        ARRAY_END,

        STRING,
        NUMBER,
        BOOLEAN,
        NULL;

        /**
         * Returns the JSON-semantic type represented by this token.
         *
         * <p>Tokens that do not themselves represent a JSON value, such as
         * {@link #NAME}, {@link #OBJECT_END}, and {@link #ARRAY_END}, map to
         * {@link JsonType#UNKNOWN}.</p>
         */
        public JsonType jsonType() {
            switch (this) {
                case OBJECT_START:
                    return JsonType.OBJECT;
                case ARRAY_START:
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

    protected NameMatcher createNameMatcher(PropertyInfo[] writableProperties) {
        return new NameMatcher(writableProperties);
    }

    /** Prepares a matcher for creator parameters and declared properties. */
    protected NameMatcher createNameMatcher(String[] names) {
        return new NameMatcher(names);
    }

    /**
     * Prepares this reader to consume one document.
     *
     * <p>The root value is not consumed.</p>
     */
    public void startDocument() throws IOException {
    }

    /**
     * Completes the current document after its root value has been consumed.
     *
     * <p>The default implementation verifies that the logical input has
     * reached the end of the document.</p>
     */
    public void endDocument() throws IOException {
        if (peekToken() != Token.EOF) {
            throw new IOException("expected end of document");
        }
    }


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
    public abstract Token peekToken() throws IOException;


    /**
     * Consumes a {@code null} value when it is next.
     *
     * <p>If the next logical value is not {@code null}, this method returns
     * {@code false} without consuming input.</p>
     *
     * @return {@code true} if {@code null} was consumed
     */
    public abstract boolean nextIfNull() throws IOException;

    /**
     * Consumes an object start when it is next.
     *
     * <p>If the next logical token is not an object start, this method
     * returns {@code false} without consuming input.</p>
     *
     * @return {@code true} if an object start was consumed
     */
    public abstract boolean nextIfObjectStart() throws IOException;

    /**
     * Consumes the end of the current object when it is next.
     *
     * <p>If another member follows, this method returns {@code false}
     * without consuming the member name.</p>
     *
     * @return {@code true} if the object end was consumed
     */
    public abstract boolean nextIfObjectEnd() throws IOException;

    /**
     * Consumes an array start when it is next.
     *
     * <p>If the next logical token is not an array start, this method
     * returns {@code false} without consuming input.</p>
     *
     * @return {@code true} if an array start was consumed
     */
    public abstract boolean nextIfArrayStart() throws IOException;

    /**
     * Consumes the end of the current array when it is next.
     *
     * <p>If another array element follows, this method returns {@code false}
     * without consuming that element.</p>
     *
     * @return {@code true} if the array end was consumed
     */
    public abstract boolean nextIfArrayEnd() throws IOException;


    /**
     * Consumes the start of an object.
     *
     * @throws IOException if the next logical token is not an object start
     */
    public abstract void startObject() throws IOException;

    /**
     * Consumes the end of the current object.
     *
     * @throws IOException if the next logical token is not an object end
     */
    public abstract void endObject() throws IOException;

    /**
     * Consumes the start of an array.
     *
     * @throws IOException if the next logical token is not an array start
     */
    public abstract void startArray() throws IOException;

    /**
     * Consumes the end of the current array.
     *
     * @throws IOException if the next logical token is not an array end
     */
    public abstract void endArray() throws IOException;


    /**
     * Consumes and returns the next member name.
     *
     * <p>If the current object has ended, the object end is consumed and
     * {@code null} is returned.</p>
     *
     * <p>When a member name is returned, its corresponding value remains
     * pending and must subsequently be consumed.</p>
     *
     * @return the next member name, or {@code null} when the current object ends
     */
    public abstract String nextName() throws IOException;

    /**
     * Advances to the next member name and matches it against prepared
     * member metadata.
     *
     * <p>If another member is present, its name is consumed and its
     * corresponding value remains pending. The value must subsequently be
     * consumed by a {@code readXxx()} method, {@link #skipNode()}, or another
     * value-consuming operation.</p>
     *
     * <p>If the enclosing object ends instead, the object end is consumed and
     * {@link NameMatcher#OBJECT_END} is returned.</p>
     *
     * <p>This is the preferred object-traversal operation for generated binding
     * code. Backends with native member-name matching should override this
     * method and match directly against the underlying input without
     * materializing the member name as a {@link String} whenever possible.</p>
     *
     * @param matcher prepared member-name matcher
     * @return a non-negative member index,
     *         {@link NameMatcher#UNKNOWN}, or
     *         {@link NameMatcher#OBJECT_END}
     */
    private String currentName;

    public int nextNameMatch(NameMatcher matcher) throws IOException {
        String name = nextName();
        currentName = name;
        return name == null ? NameMatcher.OBJECT_END : matcher.fallback(name);
    }

    /**
     * Returns the current member name while its value is pending.
     *
     * <p>Native matchers may retrieve the name directly from their parser,
     * avoiding String materialization for matched properties.</p>
     */
    public String currentName() throws IOException {
        return currentName;
    }

    /**
     * Consumes and matches the next member name, with an expected member
     * index hint.
     *
     * <p>The hint allows implementations to optimize the common case where
     * members occur in a predictable order. It does not affect matching
     * semantics and callers must not depend on the hint being honored.</p>
     *
     * <p>The corresponding member value remains pending exactly as with
     * {@link #nextNameMatch(NameMatcher)}.</p>
     *
     * @param matcher prepared member-name matcher
     * @param expectedIndex expected member index
     * @return a non-negative member index, or
     *         {@link NameMatcher#UNKNOWN}
     */
    public int nextNameMatch(NameMatcher matcher, int expectedIndex) throws IOException {
        return nextNameMatch(matcher);
    }


    /**
     * Reads and consumes the current logical value as a {@link String}.
     *
     * <p>Returns {@code null} when the current logical value is JSON
     * {@code null}.</p>
     *
     * @return string value, or {@code null}
     */
    public abstract String readString() throws IOException;


    /**
     * Reads and consumes the current logical numeric value using the
     * backend's natural {@link Number} representation.
     *
     * <p>Returns {@code null} when the current logical value is JSON
     * {@code null}.</p>
     *
     * @return numeric value, or {@code null}
     */
    public abstract Number readNumber() throws IOException;


    /**
     * Reads and consumes the current logical value as a primitive
     * {@code long}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    public abstract long readLongValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code int}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    public abstract int readIntValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code short}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    public abstract short readShortValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code byte}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    public abstract byte readByteValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code double}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    public abstract double readDoubleValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code float}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    public abstract float readFloatValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code boolean}.
     *
     * <p>JSON {@code null} is not accepted.</p>
     */
    public abstract boolean readBooleanValue() throws IOException;

    /**
     * Reads and consumes the current logical value as a primitive
     * {@code char}.
     *
     * <p>JSON {@code null} is not accepted. The accepted string
     * representation follows SJF4J binding semantics.</p>
     */
    public char readCharValue() throws IOException {
        String value = readString();
        if (value == null) {
            throw new BindingException("cannot read null as char");
        }
        if (value.length() != 1) {
            throw new BindingException("cannot read char: expected single-character string, but length was " + value.length());
        }
        return value.charAt(0);
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Long}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Long readLong() throws IOException {
        return nextIfNull() ? null : readLongValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Integer}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Integer readInt() throws IOException {
        return nextIfNull() ? null : readIntValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Short}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Short readShort() throws IOException {
        return nextIfNull() ? null : readShortValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Byte}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Byte readByte() throws IOException {
        return nextIfNull() ? null : readByteValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Double}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Double readDouble() throws IOException {
        return nextIfNull() ? null : readDoubleValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Float}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Float readFloat() throws IOException {
        return nextIfNull() ? null : readFloatValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Boolean}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Boolean readBoolean() throws IOException {
        return nextIfNull() ? null : readBooleanValue();
    }

    /**
     * Reads and consumes the current logical value as a boxed
     * {@link Character}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public Character readChar() throws IOException {
        return nextIfNull() ? null : readCharValue();
    }


    /**
     * Reads and consumes the current logical numeric value as a
     * {@link BigInteger}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public abstract BigInteger readBigInteger() throws IOException;

    /**
     * Reads and consumes the current logical numeric value as a
     * {@link BigDecimal}.
     *
     * @return the value, or {@code null} for JSON {@code null}
     */
    public abstract BigDecimal readBigDecimal() throws IOException;


    /**
     * Consumes exactly one complete logical value.
     *
     * <p>The value may be a scalar, object, or array. When called after
     * {@link #nextName()} or {@link #nextNameMatch(NameMatcher)}, this
     * method consumes the pending value of that member.</p>
     */
    public abstract void skipNode() throws IOException;


    /**
     * Reads and consumes one complete value as its natural raw OBNT node.
     *
     * <p>This is primarily a dynamic fallback operation. Statically known
     * binding code should prefer the specialized typed methods whenever
     * possible.</p>
     */
    public abstract Object readRawNode() throws IOException;

}
