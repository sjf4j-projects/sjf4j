package org.sjf4j.testbench.handwritten;

import java.io.Writer;
import java.util.Arrays;

public final class FastStringWriter extends Writer {

    // 根据你的典型 JSON 大小调。
    // 如果大部分 JSON < 1 KB，1024 是比较合理的起点。
    private static final int INITIAL_CAPACITY = 1024;

    // 防止偶然一次超大 JSON 永久占住 ThreadLocal。
    // 做纯 benchmark 时，如果 payload > 64K，可以暂时调大。
    private static final int MAX_RETAINED_CAPACITY = 64 * 1024;

    /**
     * 只缓存 char[]，不缓存 Writer。
     *
     * 这样：
     * 1. Writer 本身每次 new，TLAB 分配很便宜；
     * 2. 避免 Writer 重入问题；
     * 3. 真正昂贵的大数组被复用；
     * 4. nested serialization 也能正常工作。
     */
    private static final ThreadLocal<BufferRecycler> RECYCLER =
            ThreadLocal.withInitial(BufferRecycler::new);

    private char[] buffer;
    private int count;

    /**
     * acquire() 创建的 writer 持有对应线程的 recycler。
     * 普通构造函数创建的 writer 不参与 recycling。
     */
    private BufferRecycler recycler;

    private boolean released;

    private FastStringWriter(char[] buffer, BufferRecycler recycler) {
        this.buffer = buffer;
        this.recycler = recycler;
    }

    /**
     * 非 recycling 版本。
     */
    public FastStringWriter() {
        this(new char[INITIAL_CAPACITY], null);
    }

    /**
     * 非 recycling 版本。
     */
    public FastStringWriter(int initialCapacity) {
        this(new char[Math.max(1, initialCapacity)], null);
    }

    /**
     * 推荐的高性能入口。
     */
    public static FastStringWriter acquire() {
        BufferRecycler recycler = RECYCLER.get();

        char[] buffer = recycler.buffer;

        if (buffer == null) {
            buffer = new char[INITIAL_CAPACITY];
        } else {
            // 当前 buffer 已被取走。
            // 如果当前线程发生 nested serialization，
            // 第二个 acquire() 会拿到一个新的 buffer。
            recycler.buffer = null;
        }

        return new FastStringWriter(buffer, recycler);
    }

    @Override
    public void write(int c) {
        ensureOpen();

        int newCount = count + 1;
        ensureCapacity(newCount);

        buffer[count] = (char) c;
        count = newCount;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        ensureOpen();

        if (len == 0) {
            return;
        }

        int newCount = count + len;
        ensureCapacity(newCount);

        System.arraycopy(cbuf, off, buffer, count, len);
        count = newCount;
    }

    @Override
    public void write(char[] cbuf) {
        write(cbuf, 0, cbuf.length);
    }

    @Override
    public void write(String str, int off, int len) {
        ensureOpen();

        if (len == 0) {
            return;
        }

        int newCount = count + len;
        ensureCapacity(newCount);

        str.getChars(off, off + len, buffer, count);
        count = newCount;
    }

    @Override
    public void write(String str) {
        write(str, 0, str.length());
    }

    @Override
    public FastStringWriter append(char c) {
        write(c);
        return this;
    }

    @Override
    public FastStringWriter append(CharSequence csq) {
        if (csq == null) {
            write("null");
            return this;
        }

        if (csq instanceof String) {
            write((String) csq);
            return this;
        }

        return append(csq, 0, csq.length());
    }

    @Override
    public FastStringWriter append(
            CharSequence csq,
            int start,
            int end) {

        if (csq == null) {
            csq = "null";
        }

        int len = end - start;

        if (len <= 0) {
            return this;
        }

        if (csq instanceof String) {
            write((String) csq, start, len);
            return this;
        }

        ensureOpen();

        int newCount = count + len;
        ensureCapacity(newCount);

        for (int i = start, j = count; i < end; i++, j++) {
            buffer[j] = csq.charAt(i);
        }

        count = newCount;

        return this;
    }

    /**
     * 普通转换，不释放 buffer。
     *
     * 非 recycler 模式或者需要继续使用 Writer 时使用。
     */
    @Override
    public String toString() {
        ensureOpen();
        return new String(buffer, 0, count);
    }

    /**
     * 高性能 recycler 模式建议使用这个。
     *
     * 构造 String 后立即把 char[] 归还当前 recycler。
     * 调用后 Writer 不可继续使用。
     */
    public String toStringAndRelease() {
        ensureOpen();

        String result = new String(buffer, 0, count);

        releaseBuffer();

        return result;
    }

    /**
     * 不生成 String，直接归还 buffer。
     */
    public void release() {
        if (!released) {
            releaseBuffer();
        }
    }

    /**
     * 只适合非 release 状态下手工复用同一个 Writer。
     */
    public void reset() {
        ensureOpen();
        count = 0;
    }

    public int size() {
        return count;
    }

    public int capacity() {
        ensureOpen();
        return buffer.length;
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() {
//        release();
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= buffer.length) {
            return;
        }

        int oldCapacity = buffer.length;

        // 1.5x growth。
        // Recycler 热起来以后，大多数调用不会进入这里。
        int newCapacity = oldCapacity + (oldCapacity >> 1);

        if (newCapacity < minCapacity) {
            newCapacity = minCapacity;
        }

        if (newCapacity < 0) {
            if (minCapacity < 0) {
                throw new OutOfMemoryError();
            }
            newCapacity = Integer.MAX_VALUE - 8;
        }

        buffer = Arrays.copyOf(buffer, newCapacity);
    }

    private void releaseBuffer() {
        char[] buf = buffer;

        buffer = null;
        count = 0;
        released = true;

        BufferRecycler recycler = this.recycler;
        this.recycler = null;

        if (recycler == null || buf == null) {
            return;
        }

        // 超大 buffer 不进入缓存。
        if (buf.length > MAX_RETAINED_CAPACITY) {
            return;
        }

        char[] cached = recycler.buffer;

        // nested serialization 时可能已经有另一个 buffer 被归还。
        // 保留其中更大的一个。
        if (cached == null || buf.length > cached.length) {
            recycler.buffer = buf;
        }
    }

    private void ensureOpen() {
        if (released || buffer == null) {
            throw new IllegalStateException(
                    "FastStringWriter has already been released");
        }
    }

    private static final class BufferRecycler {
        private char[] buffer;
    }
}