package org.sjf4j.testbench.binding.handwritten;

import java.io.Writer;
import java.util.Arrays;

public final class FastStringWriter extends Writer {

    // Tune for the typical JSON size.
    // 1024 is a reasonable starting point when most JSON payloads are under 1 KB.
    private static final int INITIAL_CAPACITY = 1024;

    // Prevent an occasional oversized JSON payload from being retained by the ThreadLocal.
    // For dedicated benchmarks with payloads over 64 KB, this can be increased temporarily.
    private static final int MAX_RETAINED_CAPACITY = 64 * 1024;

    /**
     * Cache only the char[] and not the Writer itself.
     *
     * This means:
     * 1. A Writer is allocated for each call, which is cheap in the TLAB;
     * 2. Writer reentrancy is avoided;
     * 3. The expensive large array is reused; and
     * 4. Nested serialization continues to work correctly.
     */
    private static final ThreadLocal<BufferRecycler> RECYCLER =
            ThreadLocal.withInitial(BufferRecycler::new);

    private char[] buffer;
    private int count;

    /**
     * Writers created by acquire() retain the recycler for their thread.
     * Writers created by the regular constructors do not participate in recycling.
     */
    private BufferRecycler recycler;

    private boolean released;

    private FastStringWriter(char[] buffer, BufferRecycler recycler) {
        this.buffer = buffer;
        this.recycler = recycler;
    }

    /**
     * Non-recycling variant.
     */
    public FastStringWriter() {
        this(new char[INITIAL_CAPACITY], null);
    }

    /**
     * Non-recycling variant.
     */
    public FastStringWriter(int initialCapacity) {
        this(new char[Math.max(1, initialCapacity)], null);
    }

    /**
     * Recommended high-performance entry point.
     */
    public static FastStringWriter acquire() {
        BufferRecycler recycler = RECYCLER.get();

        char[] buffer = recycler.buffer;

        if (buffer == null) {
            buffer = new char[INITIAL_CAPACITY];
        } else {
            // The current buffer has been checked out.
            // If nested serialization occurs on this thread,
            // the second acquire() receives a new buffer.
            recycler.buffer = null;
        }

        return new FastStringWriter(buffer, recycler);
    }

    @Override
    public void write(int c) {
        ensureOpen();

        int newCount = count + 1;
        ensureCapacity(newCount);

        buffer[count] = (char) c;
        count = newCount;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        ensureOpen();

        if (len == 0) {
            return;
        }

        int newCount = count + len;
        ensureCapacity(newCount);

        System.arraycopy(cbuf, off, buffer, count, len);
        count = newCount;
    }

    @Override
    public void write(char[] cbuf) {
        write(cbuf, 0, cbuf.length);
    }

    @Override
    public void write(String str, int off, int len) {
        ensureOpen();

        if (len == 0) {
            return;
        }

        int newCount = count + len;
        ensureCapacity(newCount);

        str.getChars(off, off + len, buffer, count);
        count = newCount;
    }

    @Override
    public void write(String str) {
        write(str, 0, str.length());
    }

    @Override
    public FastStringWriter append(char c) {
        write(c);
        return this;
    }

    @Override
    public FastStringWriter append(CharSequence csq) {
        if (csq == null) {
            write("null");
            return this;
        }

        if (csq instanceof String) {
            write((String) csq);
            return this;
        }

        return append(csq, 0, csq.length());
    }

    @Override
    public FastStringWriter append(
            CharSequence csq,
            int start,
            int end) {

        if (csq == null) {
            csq = "null";
        }

        int len = end - start;

        if (len <= 0) {
            return this;
        }

        if (csq instanceof String) {
            write((String) csq, start, len);
            return this;
        }

        ensureOpen();

        int newCount = count + len;
        ensureCapacity(newCount);

        for (int i = start, j = count; i < end; i++, j++) {
            buffer[j] = csq.charAt(i);
        }

        count = newCount;

        return this;
    }

    /**
     * Performs a normal conversion without releasing the buffer.
     *
     * Use this outside recycler mode or when the Writer remains in use.
     */
    @Override
    public String toString() {
        ensureOpen();
        return new String(buffer, 0, count);
    }

    /**
     * Recommended for high-performance recycler mode.
     *
     * Returns the char[] to the current recycler immediately after creating the String.
     * The Writer cannot be used after this call.
     */
    public String toStringAndRelease() {
        ensureOpen();

        String result = new String(buffer, 0, count);

        releaseBuffer();

        return result;
    }

    /**
     * Returns the buffer directly without creating a String.
     */
    public void release() {
        if (!released) {
            releaseBuffer();
        }
    }

    /**
     * Suitable only for manually reusing the same Writer before it is released.
     */
    public void reset() {
        ensureOpen();
        count = 0;
    }

    public int size() {
        return count;
    }

    public int capacity() {
        ensureOpen();
        return buffer.length;
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() {
//        release();
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= buffer.length) {
            return;
        }

        int oldCapacity = buffer.length;

        // 1.5x growth.
        // Once the recycler is warm, most calls do not reach this point.
        int newCapacity = oldCapacity + (oldCapacity >> 1);

        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }

        if (newCapacity < 0) {
            if (minCapacity < 0) {
                throw new OutOfMemoryError();
            }
            newCapacity = Integer.MAX_VALUE - 8;
        }

        buffer = Arrays.copyOf(buffer, newCapacity);
    }

    private void releaseBuffer() {
        char[] buf = buffer;

        buffer = null;
        count = 0;
        released = true;

        BufferRecycler recycler = this.recycler;
        this.recycler = null;

        if (recycler == null || buf == null) {
            return;
        }

        // Do not cache oversized buffers.
        if (buf.length > MAX_RETAINED_CAPACITY) {
            return;
        }

        char[] cached = recycler.buffer;

        // Nested serialization may already have returned another buffer.
        // Retain the larger one.
        if (cached == null || buf.length > cached.length) {
            recycler.buffer = buf;
        }
    }

    private void ensureOpen() {
        if (released || buffer == null) {
            throw new IllegalStateException(
                    "FastStringWriter has already been released");
        }
    }

    private static final class BufferRecycler {
        private char[] buffer;
    }
}
