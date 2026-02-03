package org.sjf4j.facade;


import java.io.Closeable;
import java.io.IOException;

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
        UNKNOWN,     // Unknown token type
        START_OBJECT, // Start of a JSON object '{'
        END_OBJECT,   // End of a JSON object '}'
        START_ARRAY,  // Start of a JSON array '['
        END_ARRAY,    // End of a JSON array ']'
        STRING,       // JSON string value
        NUMBER,       // JSON number value
        BOOLEAN,      // JSON boolean value (true/false)
        NULL          // JSON null value
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

//    int peekTokenId() throws IOException;

    Token peekToken() throws IOException;

    default void startDocument() throws IOException {}

    default void endDocument() throws IOException {}

    void startObject() throws IOException;

    void endObject() throws IOException;

    void startArray() throws IOException;

    void endArray() throws IOException;

    String nextName() throws IOException;

    String nextString() throws IOException;

    Number nextNumber() throws IOException;

    Boolean nextBoolean() throws IOException;

    void nextNull() throws IOException;

    boolean hasNext() throws IOException;

    void skipNode() throws IOException;

}
