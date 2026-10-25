package org.sjf4j.testbench.binding.handwritten;

import java.io.Writer;

/**
 * JMH-only alternative using reusable StringBuilder/Compact String storage.
 * This is intentionally not part of the production SJF4J output layer.
 */
public final class BuilderBufferWriter extends Writer {
    private static final int INITIAL_CAPACITY = 512;
    private static final int MAX_RETAINED_CAPACITY = 64 * 1024;

    private static final class Slot {
        StringBuilder builder;
    }

    private static final ThreadLocal<Slot> LOCAL = ThreadLocal.withInitial(Slot::new);

    private final Slot slot;
    private StringBuilder builder;

    public BuilderBufferWriter() {
        slot = LOCAL.get();
        builder = slot.builder;
        slot.builder = null;
        if (builder == null) builder = new StringBuilder(INITIAL_CAPACITY);
    }

    private StringBuilder active() {
        if (builder == null) throw new IllegalStateException("Writer already recycled");
        return builder;
    }

    @Override
    public void write(int c) {
        active().append((char) c);
    }

    @Override
    public void write(char[] chars, int offset, int length) {
        active().append(chars, offset, length);
    }

    @Override
    public void write(String value, int offset, int length) {
        active().append(value, offset, offset + length);
    }

    @Override
    public void write(String value) {
        active().append(value);
    }

    public String getAndRecycle() {
        try {
            return active().toString();
        } finally {
            recycle();
        }
    }

    public void recycle() {
        StringBuilder used = builder;
        if (used == null) return;
        builder = null;
        used.setLength(0);
        if (used.capacity() <= MAX_RETAINED_CAPACITY
                && (slot.builder == null || used.capacity() > slot.builder.capacity())) {
            slot.builder = used;
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
