package org.sjf4j.binding;

import java.io.Writer;

/**
 * Unsynchronized recyclable Writer. Incremental writes use a contiguous
 * recycled char[] with a soft cache for arrays over 256K. A complete large
 * String first write is held without copying until further data is appended.
 *
 * <p>close() is deliberately a no-op: a backend may close its Writer before
 * the caller retrieves its String. The owner must call getAndRecycle() or
 * recycle().</p>
 */
public final class PooledStringWriter extends Writer {
    private final OutputBufferRecycler recycler = OutputBufferRecycler.current();
    private char[] buffer = recycler.takeChars();
    private String directString;
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
        char[] next = required > OutputBufferRecycler.MAX_RETAINED_CAPACITY
                ? recycler.takeLargeChars(required) : null;
        if (next == null)
            next = new char[OutputBufferRecycler.growCapacity(previous.length, count, required)];
        // The direct String has not been copied into previous; materialize()
        // performs the copy after ensuring capacity.
        if (directString == null)
            System.arraycopy(previous, 0, next, 0, count);
        buffer = next;
        recycler.recycleChars(previous);
        return next;
    }

    private char[] ensureCapacity(int required) {
        char[] value = active();
        return required > value.length ? grow(required) : value;
    }

    private void materialize(int required) {
        String value = directString;
        if (value != null) {
            char[] dest = ensureCapacity(required);
            value.getChars(0, count, dest, 0);
            directString = null;
        }
    }

    @Override
    public void write(int value) {
        int end = nextCount(1);
        materialize(end);
        char[] dest = ensureCapacity(end);
        dest[count] = (char) value;
        count = end;
    }

    @Override
    public void write(char[] value, int offset, int length) {
        active();
        if (offset < 0 || length < 0 || offset > value.length - length)
            throw new IndexOutOfBoundsException();
        int end = nextCount(length);
        if (length == 0) return;
        materialize(end);
        System.arraycopy(value, offset, ensureCapacity(end), count, length);
        count = end;
    }

    @Override
    public void write(String value, int offset, int length) {
        active();
        if (offset < 0 || length < 0 || offset > value.length() - length)
            throw new IndexOutOfBoundsException();
        int end = nextCount(length);
        if (length == 0) return;
        if (count == 0 && offset == 0 && length == value.length()
                && end > OutputBufferRecycler.MAX_RETAINED_CAPACITY) {
            directString = value;
        } else {
            materialize(end);
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
        active();
        CharSequence sequence = value == null ? "null" : value;
        if (start < 0 || end < start || end > sequence.length())
            throw new IndexOutOfBoundsException();
        if (sequence instanceof String) {
            write((String) sequence, start, end - start);
        } else {
            int next = nextCount(end - start);
            if (next == count) return this;
            materialize(next);
            char[] dest = ensureCapacity(next);
            for (int i = start, j = count; i < end; i++, j++)
                dest[j] = sequence.charAt(i);
            count = next;
        }
        return this;
    }

    public void reset() {
        active();
        directString = null;
        count = 0;
    }

    public int size() {
        active();
        return count;
    }

    @Override
    public String toString() {
        active();
        String value = directString;
        return value != null ? value : new String(buffer, 0, count);
    }

    public String getAndRecycle() {
        try {
            return toString();
        } finally {
            recycle();
        }
    }

    public void recycle() {
        directString = null;
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
