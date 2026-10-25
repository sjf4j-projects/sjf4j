package org.sjf4j.binding;

import java.io.Writer;
import java.util.Arrays;

/**
 * Unsynchronized in-memory Writer using a recyclable char[].
 *
 * <p>close() intentionally does not release the array: a backend may close a
 * caller-provided Writer before the caller obtains its resulting String.
 * The caller owning this buffer must call {@link #getAndRecycle()} after
 * successful output or {@link #recycle()} in a finally block.</p>
 *
 * <p>This class is not thread safe. The result String owns independent storage.</p>
 */
public final class PooledStringWriterStringChecks extends Writer {
    private char[] buffer = OutputBufferRecycler.acquireChars();
    private int count;

    public PooledStringWriterStringChecks() {}

    private char[] active() {
        char[] value = buffer;
        if (value == null) throw new IllegalStateException("Writer already recycled");
        return value;
    }

    private char[] ensureCapacity(int required) {
        char[] value = active();
        if (required > value.length) {
            buffer = value = Arrays.copyOf(value,
                    OutputBufferRecycler.growCapacity(value.length, required));
        }
        return value;
    }

    private int nextCount(int length) {
        if (length < 0 || length > Integer.MAX_VALUE - count)
            throw new IndexOutOfBoundsException("Invalid write length");
        return count + length;
    }

    /*
     * Keep the common write paths small for JIT inlining. In particular,
     * Jackson flushes the generator's char[] into this Writer repeatedly.
     * Capacity growth is cold; validation and array copy stay inline.
     */
    private char[] grow(int required) {
        return buffer = Arrays.copyOf(buffer,
                OutputBufferRecycler.growCapacity(buffer.length, required));
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
        // System.arraycopy checks the source range even when length is zero.
        System.arraycopy(value, offset, dest, count, length);
        count = end;
    }

    @Override
    public void write(String value, int offset, int length) {
        char[] dest = active();
        if (offset < 0 || length < 0 || offset > value.length() - length)
            throw new IndexOutOfBoundsException();
        int end = nextCount(length);
        if (end > dest.length) dest = grow(end);
        value.getChars(offset, offset + length, dest, count);
        count = end;
    }

    @Override
    public void write(String value) {
        write(value, 0, value.length());
    }

    @Override
    public PooledStringWriterStringChecks append(char c) {
        write(c);
        return this;
    }

    @Override
    public PooledStringWriterStringChecks append(CharSequence value) {
        CharSequence sequence = value == null ? "null" : value;
        return append(sequence, 0, sequence.length());
    }

    @Override
    public PooledStringWriterStringChecks append(CharSequence value, int start, int end) {
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

    /** Creates a detached String, then returns the working buffer to the cache. */
    public String getAndRecycle() {
        try {
            return toString();
        } finally {
            recycle();
        }
    }

    /** Recycles the working buffer; safe to call repeatedly. */
    public void recycle() {
        char[] value = buffer;
        if (value != null) {
            buffer = null;
            count = 0;
            OutputBufferRecycler.release(value);
        }
    }

    @Override
    public void flush() {}

    /** No-op: the output owner, not the backend, controls recycling. */
    @Override
    public void close() {}
}
