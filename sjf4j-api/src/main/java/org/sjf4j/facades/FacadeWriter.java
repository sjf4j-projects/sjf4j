package org.sjf4j.facades;

import java.io.Closeable;
import java.io.IOException;

public interface FacadeWriter {

    void startDocument() throws IOException;

    void endDocument() throws IOException;

    void startObject() throws IOException;

    void endObject() throws IOException;

    void startArray() throws IOException;

    void endArray() throws IOException;

    void writeName(String name) throws IOException;

    void writeValue(String value) throws IOException;

    void writeValue(Number value) throws IOException;

    void writeValue(Boolean value) throws IOException;

    void writeNull() throws IOException;

    default void writeComma() {/* For Fastjson2 */}

    void flush() throws IOException;
}
