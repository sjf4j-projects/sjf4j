package org.sjf4j.binding;

import java.io.Writer;

/**
 * Unsynchronized recyclable Writer that retains one contiguous char[].
 *
 * <p>close() is deliberately a no-op: a backend may close its Writer before
 * the caller retrieves its String. The owner must call getAndRecycle() or
 * recycle(). Arrays larger than 256K are not retained by the recycler.</p>
 */
public final class PooledStringWriter extends Writer {
    private final OutputBufferRecycler recycler = OutputBufferRecycler.current();
    private char[] buffer = recycler.takeChars();
    private int count;

    public PooledStringWriter() {}

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
        char[] next = new char[OutputBufferRecycler.growCapacity(previous.length, count, required)];
        System.arraycopy(previous, 0, next, 0, count);
        buffer = next;
        recycler.recycleChars(previous);
        if (required > OutputBufferRecycler.MAX_RETAINED_CAPACITY)
            recycler.retainMaxChars();
        return next;
    }

    private char[] ensureCapacity(int required) {
        char[] value = active();
        return required > value.length ? grow(required) : value;
    }

    @Override
    public void write(int value) {
        char[] dest = active();
        int pos = count;
        if (pos == dest.length) dest = grow(pos + 1);
        dest[pos] = (char) value;
        count = pos + 1;
    }

    @Override
    public void write(char[] value, int offset, int length) {
        char[] dest = active();
        if (length < 0 || length > Integer.MAX_VALUE - count)
            throw new IndexOutOfBoundsException();
        int end = count + length;
        if (end > dest.length) dest = grow(end);
        // System.arraycopy validates source boundaries before count changes.
        System.arraycopy(value, offset, dest, count, length);
        count = end;
    }

    @Override
    public void write(String value, int offset, int length) {
        char[] dest = active();
        if (length < 0 || length > Integer.MAX_VALUE - count)
            throw new IndexOutOfBoundsException();
        int end = count + length;
        dest = ensureCapacity(end);
        value.getChars(offset, offset + length, dest, count);
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
            char[] dest = ensureCapacity(next);
            for (int i = start, j = count; i < end; i++, j++)
                dest[j] = sequence.charAt(i);
            count = next;
        }
        return this;
    }

    public void reset() {
        active();
        count = 0;
    }

    public int size() {
        active();
        return count;
    }

    @Override
    public String toString() {
        return new String(active(), 0, count);
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
            count = 0;
            recycler.recycleChars(value);
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
