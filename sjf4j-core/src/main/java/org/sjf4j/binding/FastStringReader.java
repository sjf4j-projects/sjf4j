package org.sjf4j.binding;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/**
 * A non-synchronized {@link Reader} backed by a {@link String}.
 */
public final class FastStringReader extends Reader {

    private String str;
    private final int length;
    private int next;
    private int mark;

    public FastStringReader(String str) {
        this.str = Objects.requireNonNull(str);
        this.length = str.length();
    }

    @Override
    public int read() throws IOException {
        String s = str;
        if (s == null) {
            throw new IOException("Stream closed");
        }
        if (next >= length) {
            return -1;
        }
        return s.charAt(next++);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        String s = str;
        if (s == null) {
            throw new IOException("Stream closed");
        }
        if ((off < 0) || (off > cbuf.length) || (len < 0)
                || ((off + len) < 0) || ((off + len) > cbuf.length)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        if (next >= length) {
            return -1;
        }

        int n = Math.min(length - next, len);
        s.getChars(next, next + n, cbuf, off);
        next += n;
        return n;
    }

    @Override
    public boolean ready() throws IOException {
        if (str == null) {
            throw new IOException("Stream closed");
        }
        return true;
    }

    @Override
    public long skip(long n) throws IOException {
        if (str == null) {
            throw new IOException("Stream closed");
        }
        if (next >= length) {
            return 0;
        }

        long r = Math.min(length - next, n);
        r = Math.max(-next, r);
        next += (int) r;
        return r;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        if (readAheadLimit < 0) {
            throw new IllegalArgumentException("Read-ahead limit < 0");
        }
        if (str == null) {
            throw new IOException("Stream closed");
        }
        mark = next;
    }

    @Override
    public void reset() throws IOException {
        if (str == null) {
            throw new IOException("Stream closed");
        }
        next = mark;
    }

    @Override
    public void close() {
        str = null;
    }
}
