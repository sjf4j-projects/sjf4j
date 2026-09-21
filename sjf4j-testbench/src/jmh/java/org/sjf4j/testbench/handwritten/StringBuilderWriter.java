package org.sjf4j.testbench.handwritten;

import java.io.Writer;

/** A reusable StringBuilder-backed writer for JMH experiments. */
public final class StringBuilderWriter extends Writer {

    private static final ThreadLocal<StringBuilderWriter> WRITERS =
            ThreadLocal.withInitial(StringBuilderWriter::new);

    private final StringBuilder builder = new StringBuilder(512);

    public static StringBuilderWriter acquire() {
        StringBuilderWriter writer = WRITERS.get();
        writer.reset();
        return writer;
    }

    @Override
    public void write(int c) {
        builder.append((char) c);
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        builder.append(cbuf, off, len);
    }

    @Override
    public void write(String str) {
        str.length();
        builder.append(str);
    }

    @Override
    public void write(String str, int off, int len) {
        str.length();
        builder.append(str, off, off + len);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    public void reset() {
        builder.setLength(0);
    }

    public String getAndClear() {
        String result = builder.toString();
        reset();
        return result;
    }
}
