package org.sjf4j.binding;

import java.io.Writer;
import java.util.Objects;

/**
 * A non-synchronized {@link Writer} backed by a {@link StringBuilder}.
 */
public final class FastStringWriter extends Writer {

    private final StringBuilder builder;

    public FastStringWriter() {
        this(new StringBuilder());
    }

    public FastStringWriter(int initialSize) {
        this(new StringBuilder(initialSize));
    }

    public FastStringWriter(StringBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    public StringBuilder getBuilder() {
        return builder;
    }

    public void reset() {
        builder.setLength(0);
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
        builder.append(str);
    }

    @Override
    public void write(String str, int off, int len) {
        builder.append(str, off, off + len);
    }

    @Override
    public FastStringWriter append(char c) {
        builder.append(c);
        return this;
    }

    @Override
    public FastStringWriter append(CharSequence csq) {
        builder.append(csq);
        return this;
    }

    @Override
    public FastStringWriter append(CharSequence csq, int start, int end) {
        builder.append(csq, start, end);
        return this;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
