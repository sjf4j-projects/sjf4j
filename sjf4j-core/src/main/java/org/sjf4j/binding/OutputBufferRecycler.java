package org.sjf4j.binding;

/**
 * One byte[] and one char[] per live thread, checked out exclusively.
 *
 * <p>The cache holds raw arrays, never Writer / OutputStream instances. Taking
 * an array empties its slot, so nested serializations cannot share storage.
 * Buffers larger than 256K elements are discarded rather than pinned to
 * long-lived application threads. Retained sizes grow with observed output,
 * up to that hard limit.</p>
 */
final class OutputBufferRecycler {

    static final int INITIAL_CAPACITY = 1024;
    static final int MAX_RETAINED_CAPACITY = 256 * 1024;
    private static final int MAX_POWER_OF_TWO_CAPACITY = 64 * 1024;

    private static final ThreadLocal<OutputBufferRecycler> LOCAL =
            ThreadLocal.withInitial(OutputBufferRecycler::new);

    private byte[] bytes;
    private char[] chars;

    private OutputBufferRecycler() {}

    static byte[] acquireBytes() {
        OutputBufferRecycler recycler = LOCAL.get();
        byte[] result = recycler.bytes;
        recycler.bytes = null;
        return result != null ? result : new byte[INITIAL_CAPACITY];
    }

    static OutputBufferRecycler current() { return LOCAL.get(); }

    char[] takeChars() {
        char[] result = chars;
        chars = null;
        return result != null ? result : new char[INITIAL_CAPACITY];
    }

    void recycleChars(char[] array) {
        if (array.length > MAX_RETAINED_CAPACITY) return;
        if (chars == null || chars.length < array.length) chars = array;
    }

    static char[] acquireChars() {
        OutputBufferRecycler recycler = LOCAL.get();
        char[] result = recycler.chars;
        recycler.chars = null;
        return result != null ? result : new char[INITIAL_CAPACITY];
    }

    static void release(byte[] array) {
        if (array.length > MAX_RETAINED_CAPACITY) return;
        OutputBufferRecycler recycler = LOCAL.get();
        if (recycler.bytes == null || recycler.bytes.length < array.length) {
            recycler.bytes = array;
        }
    }

    static void release(char[] array) {
        if (array.length > MAX_RETAINED_CAPACITY) return;
        OutputBufferRecycler recycler = LOCAL.get();
        if (recycler.chars == null || recycler.chars.length < array.length) {
            recycler.chars = array;
        }
    }

    static int growCapacity(int previous, int required) {
        if (required < 0 || required > Integer.MAX_VALUE - 8)
            throw new OutOfMemoryError("Buffer too large");
        if (required <= MAX_POWER_OF_TWO_CAPACITY) {
            return Math.max(INITIAL_CAPACITY, Integer.highestOneBit(required - 1) << 1);
        }
        return (int) Math.min(Integer.MAX_VALUE - 8L,
                (long) required + (required >> 1));
    }
}
