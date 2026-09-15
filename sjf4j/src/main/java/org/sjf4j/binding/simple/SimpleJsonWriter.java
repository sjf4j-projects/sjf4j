package org.sjf4j.binding.simple;

import org.sjf4j.binding.StreamingWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * Minimal JSON writer for the built-in facade.
 */
public final class SimpleJsonWriter implements StreamingWriter {

    private final Writer writer;

    /**
     * Creates writer over output characters.
     */
    public SimpleJsonWriter(Writer output) {
        Objects.requireNonNull(output, "output");

        if (!(output instanceof BufferedWriter)) {
            output = new BufferedWriter(output);
        }
        this.writer = output;
    }

    /**
     * Starts object scope.
     */
    @Override
    public void startObject() throws IOException {
        writer.write('{');
    }

    /**
     * Ends object scope.
     */
    @Override
    public void endObject() throws IOException {
        writer.write('}');
    }

    /**
     * Starts array scope.
     */
    @Override
    public void startArray() throws IOException {
        writer.write('[');
    }

    /**
     * Ends array scope.
     */
    @Override
    public void endArray() throws IOException {
        writer.write(']');
    }

    /**
     * Writes object field name.
     */
    @Override
    public void writeName(String name) throws IOException {
        if (name == null) throw new IOException("Name must not be null");
        writeQuoted(name);
        writer.write(':');
    }

    @Override
    public void writeName(PropertyName name) throws IOException {
        Objects.requireNonNull(name, "name");
        if (name instanceof PreparedName) {
            writer.write(((PreparedName) name).encoded);
        } else {
            writeName(name.name());
        }
    }

    @Override
    public void writeStringValue(String value) throws IOException {
        if (value == null) throw new IOException("String value must not be null");
        writeQuoted(value);
    }

    @Override
    public void writeLongValue(long value) throws IOException {
        writer.write(Long.toString(value));
    }

    @Override
    public void writeIntValue(int value) throws IOException {
        writer.write(Integer.toString(value));
    }

    @Override
    public void writeShortValue(short value) throws IOException {
        writer.write(Short.toString(value));
    }

    @Override
    public void writeByteValue(byte value) throws IOException {
        writer.write(Byte.toString(value));
    }

    @Override
    public void writeDoubleValue(double value) throws IOException {
        if (!Double.isFinite(value)) throw new IOException("JSON numbers must be finite");
        writer.write(Double.toString(value));
    }

    @Override
    public void writeFloatValue(float value) throws IOException {
        if (!Float.isFinite(value)) throw new IOException("JSON numbers must be finite");
        writer.write(Float.toString(value));
    }

    @Override
    public void writeBooleanValue(boolean value) throws IOException {
        writer.write(Boolean.toString(value));
    }

    @Override
    public void writeNumberValue(Number value) throws IOException {
        Objects.requireNonNull(value, "value");
        if ((value instanceof Double && !Double.isFinite((Double) value))
                || (value instanceof Float && !Float.isFinite((Float) value))) {
            throw new IOException("JSON numbers must be finite");
        }
        writer.write(value.toString());
    }

    /**
     * Writes null value.
     */
    @Override
    public void writeNull() throws IOException {
        writer.write("null");
    }

    @Override
    public void separateProperty() throws IOException {
        writer.write(',');
    }

    @Override
    public void separateElement() throws IOException {
        writer.write(',');
    }

    /**
     * Flushes writer output.
     */
    @Override
    public void flush() throws IOException {
        writer.flush();
    }


    /// Private

    private void writeQuoted(String s) throws IOException {
        validateString(s);
        writer.write('"');
        final int len = s.length();
        int start = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    writeQuotedSpan(s, start, i);
                    writer.write("\\\"");
                    start = i + 1;
                    break;
                case '\\':
                    writeQuotedSpan(s, start, i);
                    writer.write("\\\\");
                    start = i + 1;
                    break;
                case '\b':
                    writeQuotedSpan(s, start, i);
                    writer.write("\\b");
                    start = i + 1;
                    break;
                case '\f':
                    writeQuotedSpan(s, start, i);
                    writer.write("\\f");
                    start = i + 1;
                    break;
                case '\n':
                    writeQuotedSpan(s, start, i);
                    writer.write("\\n");
                    start = i + 1;
                    break;
                case '\r':
                    writeQuotedSpan(s, start, i);
                    writer.write("\\r");
                    start = i + 1;
                    break;
                case '\t':
                    writeQuotedSpan(s, start, i);
                    writer.write("\\t");
                    start = i + 1;
                    break;
                default:
                    if (c < 0x20) {
                        writeQuotedSpan(s, start, i);
                        writeControlCharacter(c);
                        start = i + 1;
                    }
            }
        }
        writeQuotedSpan(s, start, len);
        writer.write('"');
    }

    private String quoted(String s) {
        validateName(s);
        StringBuilder output = new StringBuilder(s.length() + 2);
        output.append('"');
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': output.append("\\\""); break;
                case '\\': output.append("\\\\"); break;
                case '\b': output.append("\\b"); break;
                case '\f': output.append("\\f"); break;
                case '\n': output.append("\\n"); break;
                case '\r': output.append("\\r"); break;
                case '\t': output.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        output.append("\\u");
                        output.append(HEX[(c >>> 12) & 15]);
                        output.append(HEX[(c >>> 8) & 15]);
                        output.append(HEX[(c >>> 4) & 15]);
                        output.append(HEX[c & 15]);
                    } else {
                        output.append(c);
                    }
            }
        }
        return output.append('"').toString();
    }

    private void writeControlCharacter(char c) throws IOException {
        writer.write('\\');
        writer.write('u');
        writer.write(HEX[(c >>> 12) & 15]);
        writer.write(HEX[(c >>> 8) & 15]);
        writer.write(HEX[(c >>> 4) & 15]);
        writer.write(HEX[c & 15]);
    }

    private void writeQuotedSpan(String s, int start, int end) throws IOException {
        if (start < end) writer.write(s, start, end - start);
    }

    private static void validateString(String s) throws IOException {
        if (s == null) throw new IOException("String value must not be null");
        for (int i = 0, len = s.length(); i < len; i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 == len || !Character.isLowSurrogate(s.charAt(++i))) {
                    throw new IOException("String contains an unpaired UTF-16 surrogate");
                }
            } else if (Character.isLowSurrogate(c)) {
                throw new IOException("String contains an unpaired UTF-16 surrogate");
            }
        }
    }

    private static void validateName(String name) {
        try {
            validateString(name);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private static final class PreparedName implements PropertyName {
        private final String name;
        private final String encoded;

        private PreparedName(String name, String encoded) {
            this.name = name;
            this.encoded = encoded;
        }

        @Override
        public String name() {
            return name;
        }
    }


    /**
     * Closes underlying writer.
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }
}
