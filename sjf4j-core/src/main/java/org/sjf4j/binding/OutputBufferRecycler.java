package org.sjf4j.binding;

import java.lang.ref.SoftReference;

/**
 * Per-thread byte[] and char[] recycling, with exclusive array checkout.
 *
 * <p>Each thread retains one strongly referenced array up to 256K and one
 * softly referenced larger array of each kind. Only arrays actually allocated
 * by a writer are recycled; no standby buffers are allocated.</p>
 */
final class OutputBufferRecycler {

    static final int INITIAL_CAPACITY = 1024;
    static final int MAX_RETAINED_CAPACITY = 256 * 1024;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private static final ThreadLocal<OutputBufferRecycler> LOCAL =
            ThreadLocal.withInitial(OutputBufferRecycler::new);

    private final int strongCacheLimit;
    private byte[] bytes;
    private char[] chars;
    // Softly hold both large slots through one reusable holder. A direct
    // SoftReference<byte[]> would need a new reference on every checkout/recycle.
    private SoftReference<LargeBuffers> largeBuffers;

    private static final class LargeBuffers {
        byte[] bytes;
        char[] chars;
    }

    private LargeBuffers largeBuffers(boolean create) {
        SoftReference<LargeBuffers> ref = largeBuffers;
        LargeBuffers holder = ref == null ? null : ref.get();
        if (holder == null && create) {
            holder = new LargeBuffers();
            largeBuffers = new SoftReference<>(holder);
        }
        return holder;
    }

    private OutputBufferRecycler() { this(MAX_RETAINED_CAPACITY); }

    // Package-private to compare strong-cache caps with identical writer code in JMH.
    OutputBufferRecycler(int strongCacheLimit) {
        if (strongCacheLimit < INITIAL_CAPACITY || strongCacheLimit > MAX_RETAINED_CAPACITY)
            throw new IllegalArgumentException("strongCacheLimit");
        this.strongCacheLimit = strongCacheLimit;
    }

    static OutputBufferRecycler current() { return LOCAL.get(); }

    byte[] takeBytes() {
        byte[] result = takeLargeBytes(1);
        if (result != null) return result;
        result = bytes;
        bytes = null;
        return result != null ? result : new byte[INITIAL_CAPACITY];
    }

    char[] takeChars() {
        char[] result = takeLargeChars(1);
        if (result != null) return result;
        result = chars;
        chars = null;
        return result != null ? result : new char[INITIAL_CAPACITY];
    }

    byte[] takeLargeBytes(int required) {
        LargeBuffers holder = largeBuffers(false);
        if (holder == null) return null;
        byte[] value = holder.bytes;
        if (value == null || value.length < required) return null;
        holder.bytes = null;
        return value;
    }

    char[] takeLargeChars(int required) {
        LargeBuffers holder = largeBuffers(false);
        if (holder == null) return null;
        char[] value = holder.chars;
        if (value == null || value.length < required) return null;
        holder.chars = null;
        return value;
    }

    void recycleBytes(byte[] array) {
        if (array.length <= strongCacheLimit) {
            if (bytes == null || bytes.length < array.length) bytes = array;
        } else {
            LargeBuffers holder = largeBuffers(true);
            if (holder.bytes == null || holder.bytes.length < array.length)
                holder.bytes = array;
        }
    }

    void recycleChars(char[] array) {
        if (array.length <= strongCacheLimit) {
            if (chars == null || chars.length < array.length) chars = array;
        } else {
            LargeBuffers holder = largeBuffers(true);
            if (holder.chars == null || holder.chars.length < array.length)
                holder.chars = array;
        }
    }

    // Synthetic soft-reference eviction for the threshold microbenchmark.
    // This models cache loss, not GC pause time.
    void evictSoftCacheForBenchmark() {
        SoftReference<LargeBuffers> ref = largeBuffers;
        if (ref != null) {
            ref.clear();
            largeBuffers = null;
        }
    }

    // Keep the static accessors for other callers and existing tests.
    static byte[] acquireBytes() { return current().takeBytes(); }
    static char[] acquireChars() { return current().takeChars(); }
    static void release(byte[] array) { current().recycleBytes(array); }
    static void release(char[] array) { current().recycleChars(array); }

    /**
     * Up through 256K, use the smallest power-of-two tier from 1K.
     * Above 256K, large appends allocate exactly what is required while
     * small appends grow the previous capacity by 1.5x.
     */
    /** Byte output uses exact sizing for bulk writes and doubling for small appends. */
    static int growBytesCapacity(int previous, int used, int required) {
        if (required < 0 || required > MAX_ARRAY_SIZE)
            throw new OutOfMemoryError("Buffer too large");
        if (required <= MAX_RETAINED_CAPACITY)
            return Math.max(INITIAL_CAPACITY, Integer.highestOneBit(required - 1) << 1);
        if (used == 0 || required - used >= used) return required;
        return (int) Math.min(MAX_ARRAY_SIZE,
                Math.max((long) required, (long) previous * 2));
    }

    // Legacy experimental sink variants in testbench still use this signature.
    static int growCapacity(int previous, int required) {
        return growCapacity(previous, 0, required);
    }

    static int growCapacity(int previous, int used, int required) {
        if (required < 0 || required > MAX_ARRAY_SIZE)
            throw new OutOfMemoryError("Buffer too large");
        if (required <= MAX_RETAINED_CAPACITY) {
            return Math.max(INITIAL_CAPACITY, Integer.highestOneBit(required - 1) << 1);
        }
        if (used == 0 || required - used >= used) return required;
        return (int) Math.min(MAX_ARRAY_SIZE,
                Math.max((long) required, (long) previous + (previous >> 1)));
    }
}
