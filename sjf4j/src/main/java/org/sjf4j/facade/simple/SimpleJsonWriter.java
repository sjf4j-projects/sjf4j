package org.sjf4j.facade.simple;

import org.sjf4j.facade.StreamingWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

public class SimpleJsonWriter implements StreamingWriter {

    private final Writer writer;

    public SimpleJsonWriter(Writer output) {
        if (output == null) throw new IllegalArgumentException("Output writer must not be null");

        if (!(output instanceof BufferedWriter)) {
            output = new BufferedWriter(output);
        }
        this.writer = output;
    }

    @Override
    public void startObject() throws IOException {
        writer.write('{');
    }

    @Override
    public void endObject() throws IOException {
        writer.write('}');
    }

    @Override
    public void startArray() throws IOException {
        writer.write('[');
    }

    @Override
    public void endArray() throws IOException {
        writer.write(']');
    }

    @Override
    public void writeName(String name) throws IOException {
        if (name == null) throw new IOException("Name must not be null");
        nativeWrite(name);
        writer.write(':');
    }

    @Override
    public void writeString(String value) throws IOException {
        if (value == null) writeNull();
        else nativeWrite(value);
    }

    @Override
    public void writeNumber(Number value) throws IOException {
        if (value == null) writeNull();
        else writer.write(value.toString());
    }

    @Override
    public void writeBoolean(Boolean value) throws IOException {
        if (value == null) writeNull();
        else writer.write(value ? "true" : "false");
    }

    @Override
    public void writeNull() throws IOException {
        writer.write("null");
    }

    @Override
    public void writeArrayComma() throws IOException {
        writer.write(',');
    }

    @Override
    public void writeObjectComma() throws IOException {
        writer.write(',');
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }


    /// Private

    private void nativeWrite(String s) throws IOException {
        writer.write('"');
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': writer.write("\\\""); break;
                case '\\': writer.write("\\\\"); break;
                case '\b': writer.write("\\b"); break;
                case '\f': writer.write("\\f"); break;
                case '\n': writer.write("\\n"); break;
                case '\r': writer.write("\\r"); break;
                case '\t': writer.write("\\t"); break;
                default:
                    if (c < 0x20) {
                        // Control characters must be escaped as \\uXXXX
                        writer.write(String.format("\\u%04X", (int) c));
                    } else {
                        writer.write(c);
                    }
            }
        }
        writer.write('"');
    }


    @Override
    public void close() throws IOException {
        writer.close();
    }
}
