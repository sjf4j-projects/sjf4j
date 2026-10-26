package org.sjf4j.binding;

/**
 * Per-thread byte[] and char[] recycling, with exclusive array checkout.
 *
 * <p>Each thread retains at most one array of each kind, up to 256K elements.
 * Oversized arrays are not retained; only previously allocated reusable
 * arrays remain cached.</p>
 */
final class OutputBufferRecycler {

    static final int INITIAL_CAPACITY = 1024;
    static final int MAX_RETAINED_CAPACITY = 256 * 1024;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private static final ThreadLocal<OutputBufferRecycler> LOCAL =
            ThreadLocal.withInitial(OutputBufferRecycler::new);

    private byte[] bytes;
    private char[] chars;

    private OutputBufferRecycler() {}

    static OutputBufferRecycler current() { return LOCAL.get(); }

    byte[] takeBytes() {
        byte[] result = bytes;
        bytes = null;
        return result != null ? result : new byte[INITIAL_CAPACITY];
    }

    char[] takeChars() {
        char[] result = chars;
        chars = null;
        return result != null ? result : new char[INITIAL_CAPACITY];
    }

    void recycleBytes(byte[] array) {
        if (array.length <= MAX_RETAINED_CAPACITY && (bytes == null || bytes.length < array.length))
            bytes = array;
    }

    void recycleChars(char[] array) {
        if (array.length <= MAX_RETAINED_CAPACITY && (chars == null || chars.length < array.length))
            chars = array;
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
