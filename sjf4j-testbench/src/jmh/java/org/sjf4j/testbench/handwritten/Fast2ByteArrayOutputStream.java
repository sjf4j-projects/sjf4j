package org.sjf4j.testbench.handwritten;

import java.io.OutputStream;

public final class Fast2ByteArrayOutputStream extends OutputStream {

    private byte[] buffer;
    private int count;

    public Fast2ByteArrayOutputStream() {
        this(512);
    }

    public Fast2ByteArrayOutputStream(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                    "Negative initial capacity: " + initialCapacity);
        }
        buffer = new byte[initialCapacity];
    }

    @Override
    public void write(int b) {
        if (count == buffer.length) {
            grow(count + 1);
        }
        buffer[count++] = (byte) b;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if ((off | len | (off + len) | (b.length - off - len)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        int newCount = count + len;

        if (newCount > buffer.length) {
            grow(newCount);
        }

        System.arraycopy(b, off, buffer, count, len);
        count = newCount;
    }

    public byte[] toByteArray() {
        return java.util.Arrays.copyOf(buffer, count);
    }

    public void reset() {
        count = 0;
    }

    public int size() {
        return count;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private void grow(int minCapacity) {
        int newCapacity = Math.max(
                buffer.length << 1,
                minCapacity
        );

        buffer = java.util.Arrays.copyOf(
                buffer,
                newCapacity
        );
    }
}
