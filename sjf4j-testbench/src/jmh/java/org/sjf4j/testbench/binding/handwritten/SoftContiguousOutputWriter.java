package org.sjf4j.testbench.binding.handwritten;

import java.io.Writer;
import java.lang.ref.SoftReference;

/**
 * Benchmark-only contender: one reusable contiguous char array, no segments,
 * no StringBuilder during chunked writes. The result is constructed directly
 * from the char array, just like Jackson2's single-segment TextBuffer path.
 */
public final class SoftContiguousOutputWriter extends Writer {
    private static final int INITIAL = 1024;
    private static final int STRONG_LIMIT = 256 * 1024;
    private static final ThreadLocal<char[]> SMALL = new ThreadLocal<>();
    private static final ThreadLocal<SoftReference<char[]>> LARGE = new ThreadLocal<>();

    private char[] buffer;
    private int count;

    public SoftContiguousOutputWriter() {
        SoftReference<char[]> largeRef = LARGE.get();
        char[] large = largeRef == null ? null : largeRef.get();
        if (large != null) {
            LARGE.remove();
            buffer = large;
            return;
        }
        if (largeRef != null) LARGE.remove();
        char[] cached = SMALL.get();
        if (cached == null) buffer = new char[INITIAL];
        else {
            SMALL.remove();
            buffer = cached;
        }
    }

    private static void recycleArray(char[] array) {
        if (array.length <= STRONG_LIMIT) {
            char[] old = SMALL.get();
            if (old == null || old.length < array.length) SMALL.set(array);
        } else {
            SoftReference<char[]> ref = LARGE.get();
            char[] old = ref == null ? null : ref.get();
            if (old == null || old.length < array.length)
                LARGE.set(new SoftReference<>(array));
        }
    }

    private void grow(int required) {
        char[] next = null;
        if (required > STRONG_LIMIT) {
            SoftReference<char[]> ref = LARGE.get();
            if (ref != null) {
                char[] candidate = ref.get();
                if (candidate == null) LARGE.remove();
                else if (candidate.length >= required) {
                    LARGE.remove();
                    next = candidate;
                }
            }
        }
        if (next == null) {
            int capacity;
            if (required <= STRONG_LIMIT) {
                capacity = Math.max(INITIAL, Integer.highestOneBit(required - 1) << 1);
            } else if (count == 0 || required - count >= count) {
                capacity = required;
            } else {
                long increased = (long) buffer.length + (buffer.length >> 1);
                capacity = (int) Math.min(Integer.MAX_VALUE - 8L, Math.max(required, increased));
            }
            next = new char[capacity];
        }
        System.arraycopy(buffer, 0, next, 0, count);
        recycleArray(buffer);
        buffer = next;
    }

    @Override
    public void write(String value, int offset, int length) {
        if (buffer == null) throw new IllegalStateException("Recycled");
        if (offset < 0 || length < 0 || offset > value.length() - length)
            throw new IndexOutOfBoundsException();
        if (length > Integer.MAX_VALUE - count) throw new IndexOutOfBoundsException();
        int end = count + length;
        if (end > buffer.length) grow(end);
        value.getChars(offset, offset + length, buffer, count);
        count = end;
    }

    @Override
    public void write(char[] value, int offset, int length) {
        if (buffer == null) throw new IllegalStateException("Recycled");
        if (offset < 0 || length < 0 || offset > value.length - length)
            throw new IndexOutOfBoundsException();
        if (length > Integer.MAX_VALUE - count) throw new IndexOutOfBoundsException();
        int end = count + length;
        if (end > buffer.length) grow(end);
        System.arraycopy(value, offset, buffer, count, length);
        count = end;
    }

    @Override
    public void write(int value) {
        if (buffer == null) throw new IllegalStateException("Recycled");
        if (count == Integer.MAX_VALUE) throw new IndexOutOfBoundsException();
        if (count == buffer.length) grow(count + 1);
        buffer[count++] = (char) value;
    }

    @Override public void write(String value) { write(value, 0, value.length()); }

    public String getAndRecycle() {
        try {
            if (buffer == null) throw new IllegalStateException("Recycled");
            return new String(buffer, 0, count);
        } finally {
            recycle();
        }
    }

    public void recycle() {
        char[] current = buffer;
        if (current != null) {
            buffer = null;
            count = 0;
            recycleArray(current);
        }
    }

    @Override public void flush() { }
    @Override public void close() { }
}
