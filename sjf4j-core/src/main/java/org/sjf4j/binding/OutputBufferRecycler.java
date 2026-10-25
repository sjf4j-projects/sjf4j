package org.sjf4j.binding;

/**
 * One byte[] and one char[] per live thread, checked out exclusively.
 *
 * <p>The cache holds raw arrays, never Writer / OutputStream instances. Taking
 * an array empties its slot, so nested serializations cannot share storage.
 * Buffers larger than the retention cap are discarded rather than pinned to
 * long-lived application threads.</p>
 */
final class OutputBufferRecycler {

    static final int INITIAL_CAPACITY = 512;
    static final int MAX_RETAINED_CAPACITY = 64 * 1024;

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
        if (array.length > MAX_RETAINED_CAPACITY) return;
        if (bytes == null || bytes.length < array.length) bytes = array;
    }

    void recycleChars(char[] array) {
        if (array.length > MAX_RETAINED_CAPACITY) return;
        if (chars == null || chars.length < array.length) chars = array;
    }

    static byte[] acquireBytes() { return current().takeBytes(); }

    static char[] acquireChars() { return current().takeChars(); }

    static void release(byte[] array) { current().recycleBytes(array); }

    static void release(char[] array) { current().recycleChars(array); }

    static int growCapacity(int previous, int required) {
        if (required < 0 || required > Integer.MAX_VALUE - 8)
            throw new OutOfMemoryError("Buffer too large");
        long grown = (long) previous << 1;
        return (int) Math.min(Integer.MAX_VALUE - 8L,
                Math.max(grown, (long) required));
    }
}
