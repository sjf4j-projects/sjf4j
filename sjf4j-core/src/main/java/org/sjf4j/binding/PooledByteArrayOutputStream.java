package org.sjf4j.binding;

import java.io.OutputStream;
import java.util.Arrays;

/**
 * Unsynchronized recyclable OutputStream. The output buffer remains one
 * contiguous byte[]; above 256K it grows to 1.5x the required size.
 * Oversized arrays are discarded on recycle, retaining a separate 256K
 * buffer for subsequent smaller output.
 */
public final class PooledByteArrayOutputStream extends OutputStream {
    private final OutputBufferRecycler recycler = OutputBufferRecycler.current();
    private byte[] buffer = recycler.takeBytes();
    private int count;

    public PooledByteArrayOutputStream() {}

    private byte[] active() {
        byte[] value = buffer;
        if (value == null) throw new IllegalStateException("OutputStream already recycled");
        return value;
    }

    private byte[] ensureCapacity(int required) {
        byte[] value = active();
        if (required > value.length) {
            byte[] previous = value;
            byte[] next = new byte[OutputBufferRecycler.growCapacity(previous.length, count, required)];
            System.arraycopy(previous, 0, next, 0, count);
            buffer = value = next;
            recycler.recycleBytes(previous);
            if (required > OutputBufferRecycler.MAX_RETAINED_CAPACITY)
                recycler.retainMaxBytes();
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

    public byte[] getAndRecycle() {
        try {
            return toByteArray();
        } finally {
            recycle();
        }
    }

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

    @Override
    public void close() {}
}
