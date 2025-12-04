package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.facades.FacadeWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

public class Fastjson2Writer implements FacadeWriter {

    private final JSONWriter writer;

    public Fastjson2Writer(JSONWriter writer) {
        this.writer = writer;
    }

    @Override
    public void startDocument() throws IOException {
        // Nothing
    }

    @Override
    public void endDocument() throws IOException {
        // Nothing
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
    public void writeValue(String value) throws IOException {
        writer.writeString(value);
    }

    @Override
    public void writeValue(Number value) throws IOException {
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
    public void writeValue(Boolean value) throws IOException {
        writer.writeBool(value);
    }

    @Override
    public void writeNull() throws IOException {
        writer.writeNull();
    }

    // Ugly!
    @Override
    public void writeComma() {
        writer.writeComma();
    }

    @Override
    public void flush() {
        // nothing
    }

    // Hacking
    public void flushTo(Writer out) throws IOException {
        writer.flushTo(out);
    }
    public void flushTo(OutputStream out) throws IOException {
        writer.flushTo(out);
    }

}
