package org.sjf4j.facade.gson;

import com.google.gson.stream.JsonWriter;
import org.sjf4j.facade.FacadeWriter;

import java.io.IOException;

public class GsonWriter implements FacadeWriter {

    private final JsonWriter writer;

    public GsonWriter(JsonWriter writer) {
        this.writer = writer;
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
    public void writeString(String value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeNumber(Number value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeBoolean(Boolean value) throws IOException {
        writer.value(value);
    }

    @Override
    public void writeNull() throws IOException {
        writer.nullValue();
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

}
