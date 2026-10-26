package org.sjf4j.testbench.binding.handwritten;

import java.io.Writer;
import java.lang.ref.SoftReference;
import java.util.Arrays;

/**
 * Benchmark-only segmented character sink. Production PooledStringWriter remains unchanged.
 * A single per-thread char[] of at most 256K is retained, never allocated eagerly.
 * Completed segments are not copied during writes; only final String construction merges them.
 */
public final class SegmentedOutputWriter extends Writer {
    public enum Merge { BUILDER, ARRAY }

    private static final int INITIAL = 1024;
    private static final int MAX_RETAINED = 256 * 1024;
    private static final ThreadLocal<char[]> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<SoftReference<char[]>> LARGE_CACHE = new ThreadLocal<>();

    private static char[] take() {
        char[] a = CACHE.get();
        if (a != null) {
            CACHE.remove();
            return a;
        }
        return new char[INITIAL];
    }

    private static void release(char[] a) {
        if (a != null && a.length <= MAX_RETAINED) {
            char[] cached = CACHE.get();
            if (cached == null || cached.length < a.length) CACHE.set(a);
        } else if (a != null) {
            SoftReference<char[]> ref = LARGE_CACHE.get();
            char[] old = ref == null ? null : ref.get();
            if (old == null || old.length < a.length)
                LARGE_CACHE.set(new SoftReference<>(a));
        }
    }

    private static char[] takeLarge(int needed) {
        SoftReference<char[]> ref = LARGE_CACHE.get();
        if (ref == null) return null;
        char[] array = ref.get();
        if (array == null) {
            LARGE_CACHE.remove();
            return null;
        }
        if (array.length < needed) return null;
        LARGE_CACHE.remove();
        return array;
    }

    private char[] current = take();
    private int used;
    private int total;
    private char[][] completed;
    private int completedCount;
    private final int segmentSize;
    private final Merge merge;

    public SegmentedOutputWriter(int segmentSize, Merge merge) {
        if (segmentSize < 1)
            throw new IllegalArgumentException("segmentSize");
        this.segmentSize = segmentSize;
        this.merge = merge;
    }

    private void checkOpen() {
        if (current == null) throw new IllegalStateException("Writer recycled");
    }

    private int nextCount(int len) {
        if (len < 0 || len > Integer.MAX_VALUE - total)
            throw new IndexOutOfBoundsException("Invalid write size");
        return total + len;
    }

    private void growFirst(int required) {
        int capacity = Math.max(INITIAL, Integer.highestOneBit(required - 1) << 1);
        if (capacity > MAX_RETAINED) capacity = MAX_RETAINED;
        char[] next = new char[capacity];
        System.arraycopy(current, 0, next, 0, used);
        release(current);
        current = next;
    }

    private void completeCurrent() {
        if (completed == null) {
            completed = new char[4][];
        } else if (completedCount == completed.length) {
            completed = Arrays.copyOf(completed, completed.length * 2);
        }
        completed[completedCount++] = current;
        char[] next = segmentSize > MAX_RETAINED ? takeLarge(segmentSize) : null;
        current = next == null ? new char[segmentSize] : next;
        used = 0;
    }

    @Override
    public void write(String value, int offset, int length) {
        checkOpen();
        if (offset < 0 || length < 0 || offset > value.length() - length)
            throw new IndexOutOfBoundsException();
        int end = nextCount(length);
        if (end <= MAX_RETAINED && completedCount == 0) {
            if (end > current.length) growFirst(end);
            value.getChars(offset, offset + length, current, used);
            used = end;
            total = end;
            return;
        }
        if (completedCount == 0 && current.length < MAX_RETAINED) growFirst(MAX_RETAINED);
        while (length > 0) {
            if (used == current.length) completeCurrent();
            int n = Math.min(length, current.length - used);
            value.getChars(offset, offset + n, current, used);
            used += n;
            offset += n;
            length -= n;
        }
        total = end;
    }

    @Override
    public void write(char[] value, int offset, int length) {
        checkOpen();
        if (offset < 0 || length < 0 || offset > value.length - length)
            throw new IndexOutOfBoundsException();
        int end = nextCount(length);
        if (end <= MAX_RETAINED && completedCount == 0) {
            if (end > current.length) growFirst(end);
            System.arraycopy(value, offset, current, used, length);
            used = end;
            total = end;
            return;
        }
        if (completedCount == 0 && current.length < MAX_RETAINED) growFirst(MAX_RETAINED);
        while (length > 0) {
            if (used == current.length) completeCurrent();
            int n = Math.min(length, current.length - used);
            System.arraycopy(value, offset, current, used, n);
            used += n;
            offset += n;
            length -= n;
        }
        total = end;
    }

    @Override
    public void write(int value) {
        checkOpen();
        nextCount(1);
        if (used == current.length) {
            if (completedCount == 0 && current.length < MAX_RETAINED) {
                growFirst(Math.min(MAX_RETAINED, current.length * 2));
            } else {
                completeCurrent();
            }
        }
        current[used++] = (char) value;
        total++;
    }

    @Override
    public void write(String value) {
        write(value, 0, value.length());
    }

    public int size() {
        checkOpen();
        return total;
    }

    @Override
    public String toString() {
        checkOpen();
        if (completedCount == 0) return new String(current, 0, used);
        if (merge == Merge.BUILDER) {
            StringBuilder result = new StringBuilder(total);
            for (int i = 0; i < completedCount; i++)
                result.append(completed[i], 0, completed[i].length);
            result.append(current, 0, used);
            return result.toString();
        }
        char[] merged = new char[total];
        int dest = 0;
        for (int i = 0; i < completedCount; i++) {
            char[] a = completed[i];
            System.arraycopy(a, 0, merged, dest, a.length);
            dest += a.length;
        }
        System.arraycopy(current, 0, merged, dest, used);
        return new String(merged);
    }

    public String getAndRecycle() {
        try {
            return toString();
        } finally {
            recycle();
        }
    }

    public void recycle() {
        if (current != null) {
            release(current);
            if (completed != null) {
                for (int i = 0; i < completedCount; i++) {
                    release(completed[i]);
                    completed[i] = null;
                }
                completed = null;
            }
            current = null;
            used = total = completedCount = 0;
        }
    }

    @Override
    public void flush() { }

    @Override
    public void close() { }
}
