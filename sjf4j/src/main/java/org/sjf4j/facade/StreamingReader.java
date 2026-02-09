package org.sjf4j.facade;


import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Interface for reading JSON data in a streaming manner. This interface defines methods
 * for parsing JSON tokens, navigating through JSON structures, and extracting values.
 * It extends Closeable to ensure proper resource management.
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

    void startObject() throws IOException;

    void endObject() throws IOException;

    void startArray() throws IOException;

    void endArray() throws IOException;

    String nextName() throws IOException;

    String nextString() throws IOException;

    Number nextNumber() throws IOException;
    long nextLong() throws IOException;
    int nextInt() throws IOException;
    short nextShort() throws IOException;
    byte nextByte() throws IOException;
    double nextDouble() throws IOException;
    float nextFloat() throws IOException;
    BigInteger nextBigInteger() throws IOException;
    BigDecimal nextBigDecimal() throws IOException;

    boolean nextBoolean() throws IOException;
    void nextNull() throws IOException;

    void nextSkip() throws IOException;

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
