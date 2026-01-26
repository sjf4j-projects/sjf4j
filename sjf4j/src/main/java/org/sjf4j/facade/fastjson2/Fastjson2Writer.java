package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.facade.FacadeWriter;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

public class Fastjson2Writer implements FacadeWriter {

    private final JSONWriter writer;

    public Fastjson2Writer(JSONWriter writer) {
        this.writer = writer;
    }

    @Override
    public void startObject() throws IOException {
        writer.startObject();
    }

    @Override
    public void endObject() throws IOException {
        writer.endObject();
    }

    @Override
    public void startArray() throws IOException {
        writer.startArray();
    }

    @Override
    public void endArray() throws IOException {
        writer.endArray();
    }

    @Override
    public void writeName(String name) throws IOException {
        writer.writeName(name);
        writer.writeColon();
    }

    @Override
    public void writeString(String value) throws IOException {
        writer.writeString(value);
    }

    @Override
    public void writeNumber(Number value) throws IOException {
        if (value instanceof Long || value instanceof Integer) {
            writer.writeInt64(value.longValue());
        } else if (value instanceof Float || value instanceof Double) {
            writer.writeDouble(value.doubleValue());
        } else if (value instanceof BigInteger) {
            writer.writeBigInt(((BigInteger) value));
        } else if (value instanceof BigDecimal) {
            writer.writeDecimal(((BigDecimal) value));
        } else {
            writer.writeInt64(value.longValue());
        }
    }

    @Override
    public void writeBoolean(Boolean value) throws IOException {
        writer.writeBool(value);
    }

    @Override
    public void writeNull() throws IOException {
        writer.writeNull();
    }

    @Override
    public void writeArrayComma() {
        writer.writeComma();
    }

    @Override
    public void flush() {
        // Nothing
    }

    // Hacking
    @Override
    public void flushTo(Writer out) throws IOException {
        writer.flushTo(out);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
