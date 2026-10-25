package org.sjf4j.binding;

/** Experimental per-thread buffers with different cache limits. Not production code. */
final class CapOutputBufferRecycler {
    private static final ThreadLocal<CapOutputBufferRecycler> CAP_128 =
            ThreadLocal.withInitial(() -> new CapOutputBufferRecycler(128 * 1024));
    private static final ThreadLocal<CapOutputBufferRecycler> CAP_256 =
            ThreadLocal.withInitial(() -> new CapOutputBufferRecycler(256 * 1024));

    static CapOutputBufferRecycler cap128() { return CAP_128.get(); }
    static CapOutputBufferRecycler cap256() { return CAP_256.get(); }

    private final int cap;
    private char[] chars;
    private byte[] bytes;

    private CapOutputBufferRecycler(int cap) { this.cap = cap; }

    char[] takeChars() {
        char[] result = chars;
        chars = null;
        return result == null ? new char[1024] : result;
    }

    byte[] takeBytes() {
        byte[] result = bytes;
        bytes = null;
        return result == null ? new byte[1024] : result;
    }

    void recycleChars(char[] value) {
        if (value.length <= cap && (chars == null || chars.length < value.length))
            chars = value;
    }

    void recycleBytes(byte[] value) {
        if (value.length <= cap && (bytes == null || bytes.length < value.length))
            bytes = value;
    }

    int capacity(int oldCapacity, int required) {
        if (required <= 64 * 1024) {
            return OutputBufferRecycler.growCapacity(oldCapacity, required);
        }
        if (required < 0 || required > Integer.MAX_VALUE - 8)
            throw new OutOfMemoryError("Buffer too large");
        long grown = (long) required + (required >> 1);
        // Retainable sizes should not overshoot cache cap. In particular
        // 128K write -> 128K array for cap128, 192K array for cap256.
        if (required <= cap) return (int) Math.min(cap, grown);
        return (int) Math.min(Integer.MAX_VALUE - 8L, grown);
    }
}
