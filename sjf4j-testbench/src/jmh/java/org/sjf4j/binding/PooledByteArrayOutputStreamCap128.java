package org.sjf4j.binding;

import java.io.OutputStream;
import java.util.Arrays;

/**
 * Unsynchronized recyclable alternative to ByteArrayOutputStream.
 *
 * <p>The caller owns recycling. close() is deliberately a no-op so that
 * backends may close a supplied OutputStream before the caller retrieves the
 * result. Call {@link #getAndRecycle()} or {@link #recycle()} after use.</p>
 */
public final class PooledByteArrayOutputStreamCap128 extends OutputStream {
    private final CapOutputBufferRecycler recycler = CapOutputBufferRecycler.cap128();
    private byte[] buffer = recycler.takeBytes();
    private int count;

    public PooledByteArrayOutputStreamCap128() {}

    private byte[] active() {
        byte[] value = buffer;
        if (value == null) throw new IllegalStateException("OutputStream already recycled");
        return value;
    }

    private byte[] ensureCapacity(int required) {
        byte[] value = active();
        if (required > value.length) {
            byte[] previous = value;
            byte[] next = new byte[recycler.capacity(previous.length, required)];
            System.arraycopy(previous, 0, next, 0, count);
            buffer = value = next;
            recycler.recycleBytes(previous);
        }
        return value;
    }

    private int nextCount(int length) {
        if (length < 0 || length > Integer.MAX_VALUE - count)
            throw new IndexOutOfBoundsException("Invalid write length");
        return count + length;
    }

    @Override
    public void write(int value) {
        byte[] dest = ensureCapacity(nextCount(1));
        dest[count++] = (byte) value;
    }

    @Override
    public void write(byte[] value, int offset, int length) {
        if (offset < 0 || length < 0 || offset > value.length - length)
            throw new IndexOutOfBoundsException();
        if (length == 0) { active(); return; }
        int end = nextCount(length);
        System.arraycopy(value, offset, ensureCapacity(end), count, length);
        count = end;
    }

    public int size() {
        active();
        return count;
    }

    public void reset() {
        active();
        count = 0;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(active(), count);
    }

    /** Copies the result, then returns the working buffer to the cache. */
    public byte[] getAndRecycle() {
        try {
            return toByteArray();
        } finally {
            recycle();
        }
    }

    /** Recycles the working buffer; safe to call repeatedly. */
    public void recycle() {
        byte[] value = buffer;
        if (value != null) {
            buffer = null;
            count = 0;
            recycler.recycleBytes(value);
        }
    }

    @Override
    public void flush() {}

    /** No-op: the output owner, not the backend, controls recycling. */
    @Override
    public void close() {}
}
