package org.sjf4j.backend.gson.binding;

import com.google.gson.stream.JsonWriter;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.binding.StreamingWriter;
import org.sjf4j.util.Asserts;

import java.io.IOException;

/** StreamingWriter backed directly by a Gson {@link JsonWriter}. */
public final class GsonWriter extends StreamingWriter {

    private final JsonWriter writer;

    public GsonWriter(JsonWriter writer) {
        super(Backend.GSON);
        this.writer = Asserts.notNull(writer, "writer");
    }

    @Override
    public void startObject() throws IOException {
        writer.beginObject();
    }

    @Override
    public void endObject() throws IOException {
        writer.endObject();
    }

    @Override
    public void startArray() throws IOException {
        writer.beginArray();
    }

    @Override
    public void endArray() throws IOException {
        writer.endArray();
    }

    @Override
    public void writeName(String name) throws IOException {
        writer.name(name);
    }

    @Override
    public void writeNull() throws IOException {
        writer.nullValue();
    }

    @Override
    public void writeStringValue(String value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeLongValue(long value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeIntValue(int value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeShortValue(short value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeByteValue(byte value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeDoubleValue(double value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeFloatValue(float value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeBooleanValue(boolean value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeCharValue(char value) throws IOException {
        writer.value(Character.toString(value));
    }

    @Override
    public void writeNumberValue(Number value) throws IOException {
        writer.value(value);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
