package org.sjf4j.binding;

import java.io.Writer;

/**
 * Unsynchronized recyclable Writer. Uses a cached char[] for output up
 * through 256K characters, and StringBuilder for larger output. Closing
 * the Writer does not release the result; its owner calls getAndRecycle()
 * or recycle() explicitly.
 */
public final class PooledStringWriter extends Writer {
    private final OutputBufferRecycler recycler = OutputBufferRecycler.current();
    private char[] buffer = recycler.takeChars();
    private StringBuilder large;
    private int count;

    public PooledStringWriter() {}

    private char[] active() {
        char[] value = buffer;
        if (value == null) throw new IllegalStateException("Writer already recycled or in large mode");
        return value;
    }

    private int nextCount(int length) {
        if (length < 0 || length > Integer.MAX_VALUE - count)
            throw new IndexOutOfBoundsException("Invalid write length");
        return count + length;
    }

    private StringBuilder switchToLarge(int required) {
        char[] previous = active();
        StringBuilder builder = new StringBuilder(required);
        builder.append(previous, 0, count);
        recycler.recycleChars(previous);
        buffer = null;
        large = builder;
        recycler.retainMaxChars();
        return builder;
    }

    private char[] grow(int required) {
        char[] previous = active();
        char[] next = new char[OutputBufferRecycler.growCapacity(previous.length, required)];
        System.arraycopy(previous, 0, next, 0, count);
        buffer = next;
        recycler.recycleChars(previous);
        return next;
    }

    @Override
    public void write(int value) {
        StringBuilder builder = large;
        if (builder != null) {
            builder.append((char) value);
            count++;
            return;
        }
        char[] dest = active();
        int end = nextCount(1);
        if (end > OutputBufferRecycler.MAX_RETAINED_CAPACITY) {
            switchToLarge(end).append((char) value);
        } else {
            if (end > dest.length) dest = grow(end);
            dest[count] = (char) value;
        }
        count = end;
    }

    @Override
    public void write(char[] value, int offset, int length) {
        int end = nextCount(length);
        StringBuilder builder = large;
        if (builder != null) {
            builder.append(value, offset, length);
        } else {
            char[] dest = active();
            if (end > OutputBufferRecycler.MAX_RETAINED_CAPACITY) {
                switchToLarge(end).append(value, offset, length);
            } else {
                if (end > dest.length) dest = grow(end);
                System.arraycopy(value, offset, dest, count, length);
            }
        }
        count = end;
    }

    @Override
    public void write(String value, int offset, int length) {
        int end = nextCount(length);
        StringBuilder builder = large;
        if (builder != null) {
            builder.append(value, offset, offset + length);
        } else {
            char[] dest = active();
            if (end > OutputBufferRecycler.MAX_RETAINED_CAPACITY) {
                switchToLarge(end).append(value, offset, offset + length);
            } else {
                if (end > dest.length) dest = grow(end);
                value.getChars(offset, offset + length, dest, count);
            }
        }
        count = end;
    }

    @Override
    public void write(String value) {
        write(value, 0, value.length());
    }

    @Override
    public PooledStringWriter append(char c) {
        write(c);
        return this;
    }

    @Override
    public PooledStringWriter append(CharSequence value) {
        CharSequence sequence = value == null ? "null" : value;
        return append(sequence, 0, sequence.length());
    }

    @Override
    public PooledStringWriter append(CharSequence value, int start, int end) {
        CharSequence sequence = value == null ? "null" : value;
        if (start < 0 || end < start || end > sequence.length())
            throw new IndexOutOfBoundsException();
        if (sequence instanceof String) {
            write((String) sequence, start, end - start);
        } else {
            int next = nextCount(end - start);
            StringBuilder builder = large;
            if (builder != null) {
                builder.append(sequence, start, end);
            } else {
                char[] dest = active();
                if (next > OutputBufferRecycler.MAX_RETAINED_CAPACITY) {
                    switchToLarge(next).append(sequence, start, end);
                } else {
                    if (next > dest.length) dest = grow(next);
                    for (int i = start, j = count; i < end; i++, j++)
                        dest[j] = sequence.charAt(i);
                }
            }
            count = next;
        }
        return this;
    }

    public void reset() {
        if (large != null) {
            large = null;
            buffer = recycler.takeChars();
        } else {
            active();
        }
        count = 0;
    }

    public int size() {
        if (large == null) active();
        return count;
    }

    @Override
    public String toString() {
        StringBuilder builder = large;
        return builder != null ? builder.toString() : new String(active(), 0, count);
    }

    public String getAndRecycle() {
        try {
            return toString();
        } finally {
            recycle();
        }
    }

    public void recycle() {
        char[] value = buffer;
        if (value != null) {
            buffer = null;
            recycler.recycleChars(value);
        }
        large = null;
        count = 0;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
