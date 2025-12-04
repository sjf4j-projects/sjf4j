package org.sjf4j.facades;


import java.io.Closeable;
import java.io.IOException;

public interface FacadeReader extends Closeable {

    enum Token {
        UNKNOWN,
        START_OBJECT,
        END_OBJECT,
        START_ARRAY,
        END_ARRAY,
        NAME,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    int ID_UNKNOWN = -1;
    int ID_START_OBJECT = 1;
    int ID_END_OBJECT = 2;
    int ID_START_ARRAY = 3;
    int ID_END_ARRAY = 4;
    int ID_NAME = 5;
    int ID_STRING = 6;
    int ID_NUMBER = 7;
    int ID_BOOLEAN = 8;
    int ID_NULL = 9;

    int peekTokenId() throws IOException;

    void startDocument() throws IOException;

    void endDocument() throws IOException;

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

//    default boolean hasNext() throws IOException {
//        int tid = peekTokenId();
//        return tid != ID_END_OBJECT && tid != ID_END_ARRAY;
//    }

}
