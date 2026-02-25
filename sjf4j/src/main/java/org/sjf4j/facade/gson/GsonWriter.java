package org.sjf4j.facade.gson;

import com.google.gson.stream.JsonWriter;
import org.sjf4j.facade.StreamingWriter;

import java.io.IOException;

/**
 * Streaming writer backed by Gson's {@link JsonWriter}.
 */
public class GsonWriter implements StreamingWriter {

    private final JsonWriter writer;

    /**
     * Creates writer adapter from Gson JsonWriter.
     */
    public GsonWriter(JsonWriter writer) {
        this.writer = writer;
    }

    /**
     * Starts object scope.
     */
    @Override
    public void startObject() throws IOException {
        writer.beginObject();
    }

    /**
     * Ends object scope.
     */
    @Override
    public void endObject() throws IOException {
        writer.endObject();
    }

    /**
     * Starts array scope.
     */
    @Override
    public void startArray() throws IOException {
        writer.beginArray();
    }

    /**
     * Ends array scope.
     */
    @Override
    public void endArray() throws IOException {
        writer.endArray();
    }

    /**
     * Writes object field name.
     */
    @Override
    public void writeName(String name) throws IOException {
        writer.name(name);
    }

    /**
     * Writes string value.
     */
    @Override
    public void writeString(String value) throws IOException {
        writer.value(value);
    }

    /**
     * Writes numeric value.
     */
    @Override
    public void writeNumber(Number value) throws IOException {
        writer.value(value);
    }

    /**
     * Writes boolean value.
     */
    @Override
    public void writeBoolean(Boolean value) throws IOException {
        writer.value(value);
    }

    /**
     * Writes null value.
     */
    @Override
    public void writeNull() throws IOException {
        writer.nullValue();
    }

    /**
     * Flushes writer output.
     */
    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    /**
     * Closes underlying writer.
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
