package org.sjf4j.facade;


import org.sjf4j.JsonType;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Streaming reader abstraction for structured inputs.
 */
public interface StreamingReader extends Closeable {

    /**
     * Enum representing structural token types.
     */
    enum Token {
        EOF,            // End of streaming
        UNKNOWN,        // Unknown token type
        START_OBJECT,   // Start of an object-like structure (for JSON, '{')
        END_OBJECT,     // End of an object-like structure (for JSON, '}')
        FIELD_NAME,     // Field name in an object-like structure
        START_ARRAY,    // Start of an array-like structure (for JSON, '[')
        END_ARRAY,      // End of an array-like structure (for JSON, ']')
        STRING,         // JSON string value
        NUMBER,         // JSON number value
        BOOLEAN,        // JSON boolean value (true/false)
        NULL;          // JSON null value

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

//    int ID_UNKNOWN = -1;
//    int ID_START_OBJECT = 1;
//    int ID_END_OBJECT = 2;
//    int ID_START_ARRAY = 3;
//    int ID_END_ARRAY = 4;
//    int ID_NAME = 5;
//    int ID_STRING = 6;
//    int ID_NUMBER = 7;
//    int ID_BOOLEAN = 8;
//    int ID_NULL = 9;

    /**
     * Prepares this reader to consume one document without consuming its root value.
     */
    default void startDocument() throws IOException {}

    /**
     * Completes a document after its root value has been consumed.
     * Implementations must reject remaining content that is invalid for their input format.
     */
    default void endDocument() throws IOException {
        if (peekToken() != Token.EOF) throw new IOException("Expected end of document");
    }


    /**
     * Returns the current token without consuming it. {@link Token#EOF} means no further input remains.
     */
    Token peekToken() throws IOException;

    /**
     * Consumes a null value when it is the current token.
     *
     * @return {@code true} when a null token was consumed; {@code false} otherwise
     */
    default boolean nextIfNull() throws IOException {
        if (peekToken() != Token.NULL) return false;
        nextNull();
        return true;
    }

    /**
     * Consumes an object end token when it is the current token.
     *
     * @return {@code true} when an object end token was consumed; {@code false} otherwise
     */
    default boolean nextIfObjectEnd() throws IOException {
        if (peekToken() != Token.END_OBJECT) return false;
        endObject();
        return true;
    }

    /**
     * Consumes an array end token when it is the current token.
     *
     * @return {@code true} when an array end token was consumed; {@code false} otherwise
     */
    default boolean nextIfArrayEnd() throws IOException {
        if (peekToken() != Token.END_ARRAY) return false;
        endArray();
        return true;
    }

    /**
     * Buffers the current complete value and returns a reader over that isolated copy.
     * The source reader is advanced past the value. This is optional because buffering
     * costs memory; callers must handle a {@code null} result.
     *
     * <p>Used for discriminator-based {@code OneOf} binding when a backend's hosting
     * databind framework needs to continue reading the original parser after a custom
     * field deserializer returns. The fork can inspect and bind the value without
     * disturbing that framework's parser cursor contract.</p>
     */
    default StreamingReader forkValue() throws IOException {
        return null;
    }

    void startObject() throws IOException;

    void endObject() throws IOException;

    void startArray() throws IOException;

    void endArray() throws IOException;

    String nextName() throws IOException;

    String nextString() throws IOException;

    Number nextNumber() throws IOException;
    Long nextLong() throws IOException;
    Integer nextInt() throws IOException;
    Short nextShort() throws IOException;
    Byte nextByte() throws IOException;
    Double nextDouble() throws IOException;
    Float nextFloat() throws IOException;
    BigInteger nextBigInteger() throws IOException;
    BigDecimal nextBigDecimal() throws IOException;

    Boolean nextBoolean() throws IOException;
    void nextNull() throws IOException;

    /**
     * Reads and consumes current value as a {@code long}; conversion, error and cursor behavior follows underlying reader.
     */
    default long nextLongValue() throws IOException {
        return nextLong();
    }

    /**
     * Reads and consumes current value as an {@code int}; conversion, error and cursor behavior follows underlying reader.
     */
    default int nextIntValue() throws IOException {
        return nextInt();
    }

    /**
     * Reads and consumes current value as a {@code short}; conversion, error and cursor behavior follows underlying reader.
     */
    default short nextShortValue() throws IOException {
        return nextShort();
    }

    /**
     * Reads and consumes current value as a {@code byte}; conversion, error and cursor behavior follows underlying reader.
     */
    default byte nextByteValue() throws IOException {
        return nextByte();
    }

    /**
     * Reads and consumes current value as a {@code double}; conversion, error and cursor behavior follows underlying reader.
     */
    default double nextDoubleValue() throws IOException {
        return nextDouble();
    }

    /**
     * Reads and consumes current value as a {@code float}; conversion, error and cursor behavior follows underlying reader.
     */
    default float nextFloatValue() throws IOException {
        return nextFloat();
    }

    /**
     * Reads and consumes current value as a {@code boolean}; conversion, error and cursor behavior follows underlying reader.
     */
    default boolean nextBooleanValue() throws IOException {
        return nextBoolean();
    }

    /**
     * Consumes exactly one complete value at the current position.
     * Implementations must fail when no value is available.
     */
    void skipNext() throws IOException;

//    Token nextToken() throws IOException;
//
//    String peekName() throws IOException;
//
//    String peekString() throws IOException;
//
//    Number peekNumber() throws IOException;
//    long peekLong() throws IOException;
//    int peekInt() throws IOException;
//    short peekShort() throws IOException;
//    byte peekByte() throws IOException;
//    double peekDouble() throws IOException;
//    float peekFloat() throws IOException;
//    BigInteger peekBigInteger() throws IOException;
//    BigDecimal peekBigDecimal() throws IOException;
//
//    boolean peekBoolean() throws IOException;

}
