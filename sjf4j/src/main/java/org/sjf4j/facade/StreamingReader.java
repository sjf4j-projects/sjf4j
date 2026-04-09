package org.sjf4j.facade;


import org.sjf4j.JsonType;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Streaming reader abstraction for JSON-like inputs.
 */
public interface StreamingReader extends Closeable {

    /**
     * Enum representing JSON token types.
     */
    enum Token {
        EOF,            // End of streaming
        UNKNOWN,        // Unknown token type
        START_OBJECT,   // Start of a JSON object '{'
        END_OBJECT,     // End of a JSON object '}'
        FIELD_NAME,     // Field name in JSON object
        START_ARRAY,    // Start of a JSON array '['
        END_ARRAY,      // End of a JSON array ']'
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

    default void startDocument() throws IOException {}

    default void endDocument() throws IOException {}


    Token peekToken() throws IOException;

    /**
     * Returns an isolated reader for the current JSON value when the backend can buffer it.
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
