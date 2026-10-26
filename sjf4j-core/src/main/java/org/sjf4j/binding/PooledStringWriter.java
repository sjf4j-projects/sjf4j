package org.sjf4j.binding;

import java.io.Writer;

/**
 * Unsynchronized recyclable Writer. Up to 256K characters it uses a pooled
 * char[]; incremental large outputs keep a contiguous softly cached char[].
 * A first full oversized String uses StringBuilder(value) for compact strings.
 *
 * <p>close() is deliberately a no-op: a backend may close its Writer before
 * the caller retrieves its String. The owner must call getAndRecycle() or
 * recycle(). The large StringBuilder is never retained after recycle/reset.</p>
 */
public final class PooledStringWriter extends Writer {
    private final OutputBufferRecycler recycler = OutputBufferRecycler.current();
    private char[] buffer = recycler.takeChars();
    private StringBuilder large;
    private int count;

    public PooledStringWriter() {}

    private void checkOpen() {
        if (buffer == null && large == null)
            throw new IllegalStateException("Writer already recycled");
    }

    private char[] active() {
        char[] value = buffer;
        if (value == null) throw new IllegalStateException("Writer already recycled");
        return value;
    }

    private int nextCount(int length) {
        if (length < 0 || length > Integer.MAX_VALUE - count)
            throw new IndexOutOfBoundsException("Invalid write length");
        return count + length;
    }

    private char[] grow(int required) {
        char[] previous = active();
        char[] next = required > OutputBufferRecycler.MAX_RETAINED_CAPACITY
                ? recycler.takeLargeChars(required) : null;
        if (next == null)
            next = new char[OutputBufferRecycler.growCapacity(previous.length, count, required)];
        System.arraycopy(previous, 0, next, 0, count);
        buffer = next;
        recycler.recycleChars(previous);
        return next;
    }

    private char[] ensureCapacity(int required) {
        char[] value = active();
        return required > value.length ? grow(required) : value;
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
        int pos = count;
        if (pos == dest.length) dest = grow(pos + 1);
        dest[pos] = (char) value;
        count = pos + 1;
    }

    @Override
    public void write(char[] value, int offset, int length) {
        checkOpen();
        if (offset < 0 || length < 0 || offset > value.length - length)
            throw new IndexOutOfBoundsException();
        int end = nextCount(length);
        StringBuilder builder = large;
        if (builder != null) {
            builder.append(value, offset, length);
        } else {
            System.arraycopy(value, offset, ensureCapacity(end), count, length);
        }
        count = end;
    }

    @Override
    public void write(String value, int offset, int length) {
        checkOpen();
        if (offset < 0 || length < 0 || offset > value.length() - length)
            throw new IndexOutOfBoundsException();
        int end = nextCount(length);
        StringBuilder builder = large;
        if (builder != null) {
            builder.append(value, offset, offset + length);
        } else if (count == 0 && end > OutputBufferRecycler.MAX_RETAINED_CAPACITY
                && offset == 0 && length == value.length()) {
            // Preserve source coder when the complete first String is oversized.
            char[] previous = active();
            large = new StringBuilder(value);
            buffer = null;
            recycler.recycleChars(previous);
        } else {
            value.getChars(offset, offset + length, ensureCapacity(end), count);
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
        checkOpen();
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
                char[] dest = ensureCapacity(next);
                for (int i = start, j = count; i < end; i++, j++)
                    dest[j] = sequence.charAt(i);
            }
            count = next;
        }
        return this;
    }

    public void reset() {
        checkOpen();
        if (large != null) {
            large = null;
            buffer = recycler.takeChars();
        }
        count = 0;
    }

    public int size() {
        checkOpen();
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
        large = null;
        char[] value = buffer;
        if (value != null) {
            buffer = null;
            recycler.recycleChars(value);
        }
        count = 0;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
