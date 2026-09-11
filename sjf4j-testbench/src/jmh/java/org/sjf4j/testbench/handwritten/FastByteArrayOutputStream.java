package org.sjf4j.testbench.handwritten;

import java.io.OutputStream;
import java.util.Arrays;

/**
 * High-performance byte-array OutputStream optimized for short-lived
 * serialization workloads.
 *
 * <p>The stream object itself is cheap and is not pooled.
 * Its backing byte[] is recycled per thread.</p>
 *
 * <p>close() and flush() are intentionally no-ops so this class can be
 * safely used as a target of JSON generators that close their output
 * target. Call {@link #toByteArrayAndRelease()} after serialization.</p>
 */
public final class FastByteArrayOutputStream extends OutputStream {

    /**
     * Jackson's BYTE_WRITE_CONCAT_BUFFER also starts at 2000 bytes.
     * This is a reasonable baseline for JSON serialization.
     */
    private static final int INITIAL_CAPACITY = 1024;

    /**
     * Do not retain accidentally huge output buffers forever.
     *
     * Benchmark 32K / 64K / 128K for your workload.
     */
    private static final int MAX_RETAINED_CAPACITY = 64 * 1024;

    private static final ThreadLocal<BufferRecycler> RECYCLER =
            ThreadLocal.withInitial(BufferRecycler::new);

    private byte[] buffer;
    private int count;

    private BufferRecycler recycler;
    private boolean released;

    private FastByteArrayOutputStream(byte[] buffer,
                                      BufferRecycler recycler) {
        this.buffer = buffer;
        this.recycler = recycler;
    }

    /**
     * Creates a non-recycling stream.
     */
    public FastByteArrayOutputStream() {
        this(new byte[INITIAL_CAPACITY], null);
    }

    /**
     * Creates a non-recycling stream with explicit initial capacity.
     */
    public FastByteArrayOutputStream(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                    "Negative initial capacity: " + initialCapacity);
        }

        this.buffer = new byte[Math.max(1, initialCapacity)];
    }

    /**
     * Acquires a stream backed by a thread-local recycled byte[].
     *
     * <p>Only the byte[] is pooled. The stream wrapper itself is new and
     * intentionally cheap.</p>
     */
    public static FastByteArrayOutputStream acquire() {
        BufferRecycler recycler = RECYCLER.get();

        byte[] buffer = recycler.buffer;

        if (buffer == null) {
            buffer = new byte[INITIAL_CAPACITY];
        } else {
            /*
             * Detach while in use.
             *
             * This makes nested acquire() calls safe: a nested serializer
             * simply gets a fresh buffer instead of corrupting the outer one.
             */
            recycler.buffer = null;
        }

        return new FastByteArrayOutputStream(buffer, recycler);
    }

    @Override
    public void write(int b) {
        ensureOpen();

        if (count == buffer.length) {
            grow(count + 1);
        }

        buffer[count++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        ensureOpen();

        if (b == null) {
            throw new NullPointerException("b");
        }

        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return;
        }

        int newCount = count + len;

        if (newCount < 0) {
            throw new OutOfMemoryError("Required array size too large");
        }

        if (newCount > buffer.length) {
            grow(newCount);
        }

        System.arraycopy(b, off, buffer, count, len);
        count = newCount;
    }

    @Override
    public void write(byte[] b) {
        write(b, 0, b.length);
    }

    /**
     * Number of bytes currently written.
     */
    public int size() {
        ensureOpen();
        return count;
    }

    /**
     * Current backing-buffer capacity.
     */
    public int capacity() {
        ensureOpen();
        return buffer.length;
    }

    /**
     * Clears content while retaining the current backing array.
     *
     * Mainly useful for manually managed, non-released instances.
     */
    public void reset() {
        ensureOpen();
        count = 0;
    }

    /**
     * Returns an independent byte[] containing the current content.
     *
     * The stream remains usable after this call.
     */
    public byte[] toByteArray() {
        ensureOpen();

        if (count == 0) {
            return new byte[0];
        }

        return Arrays.copyOf(buffer, count);
    }

    /**
     * Returns the result and releases the backing buffer to the recycler.
     *
     * The stream must not be used afterwards.
     */
    public byte[] toByteArrayAndRelease() {
        ensureOpen();

        final byte[] result;

        if (count == 0) {
            result = new byte[0];
        } else {
            result = Arrays.copyOf(buffer, count);
        }

        releaseBuffer();

        return result;
    }

    /**
     * Releases the backing buffer without constructing a result.
     */
    public void release() {
        if (!released) {
            releaseBuffer();
        }
    }

    /**
     * Intentionally does not release the buffer.
     *
     * JSON generators such as Jackson may close their target OutputStream
     * before the caller retrieves the accumulated result.
     */
    @Override
    public void close() {
        // no-op
    }

    @Override
    public void flush() {
        // no-op
    }

    private void grow(int minCapacity) {
        int oldCapacity = buffer.length;

        /*
         * 1.5x growth.
         *
         * Growth strategy matters mainly on the first oversized request:
         * after recycling has warmed up, normal workloads should rarely
         * enter this method.
         */
        int newCapacity = oldCapacity + (oldCapacity >> 1);

        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }

        if (newCapacity < 0) {
            if (minCapacity < 0) {
                throw new OutOfMemoryError(
                        "Required array size too large");
            }

            newCapacity = Integer.MAX_VALUE - 8;
        }

        buffer = Arrays.copyOf(buffer, newCapacity);
    }

    private void releaseBuffer() {
        byte[] buf = buffer;
        BufferRecycler recycler = this.recycler;

        buffer = null;
        this.recycler = null;
        count = 0;
        released = true;

        if (buf == null || recycler == null) {
            return;
        }

        /*
         * Avoid retaining a one-off huge JSON payload in a ThreadLocal.
         */
        if (buf.length > MAX_RETAINED_CAPACITY) {
            return;
        }

        /*
         * Nested serialization may already have returned another buffer.
         * Keep the larger one, since it is more useful for later calls.
         */
        byte[] cached = recycler.buffer;

        if (cached == null || buf.length > cached.length) {
            recycler.buffer = buf;
        }
    }

    private void ensureOpen() {
        if (released || buffer == null) {
            throw new IllegalStateException(
                    "FastByteArrayOutputStream has already been released");
        }
    }

    private static final class BufferRecycler {
        byte[] buffer;
    }
}