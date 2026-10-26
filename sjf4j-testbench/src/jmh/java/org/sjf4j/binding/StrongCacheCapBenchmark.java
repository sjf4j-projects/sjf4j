package org.sjf4j.binding;

import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Compare two strong-reference retention limits using the actual production
 * PooledStringWriter/PooledByteArrayOutputStream and OutputBufferRecycler.
 *
 * Only the strong retention limit varies. Power-of-two growth through 256K,
 * large String direct pass-through threshold and soft-first policy stay fixed.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Threads(1)
public class StrongCacheCapBenchmark {
    private static final int CHUNK = 256;

    @Param({"65536", "262144"})
    public int strongLimit;

    @Param({"1024", "8192", "65536", "131072", "262144", "307200", "1048576"})
    public int payloadLength;

    @Param({"ASCII", "UNICODE"})
    public String alphabet;

    private OutputBufferRecycler recycler;
    private String data;
    private byte[] bytes;

    @Setup(Level.Trial)
    public void setUp() {
        recycler = new OutputBufferRecycler(strongLimit);
        String pattern = "ASCII".equals(alphabet) ? "hello-1234" : "中文测试例";
        data = pattern.repeat((payloadLength + pattern.length() - 1) / pattern.length())
                .substring(0, payloadLength);
        bytes = data.getBytes(StandardCharsets.UTF_8);

        PooledStringWriter writer = new PooledStringWriter(recycler);
        try {
            writeChars(writer);
            if (!data.equals(writer.getAndRecycle())) {
                throw new IllegalStateException("Incorrect char output");
            }
        } finally {
            writer.recycle();
        }
        PooledByteArrayOutputStream stream = new PooledByteArrayOutputStream(recycler);
        try {
            writeBytes(stream);
            if (!Arrays.equals(bytes, stream.getAndRecycle())) {
                throw new IllegalStateException("Incorrect byte output");
            }
        } finally {
            stream.recycle();
        }
    }

    private void writeChars(PooledStringWriter writer) {
        for (int off = 0; off < data.length(); off += CHUNK) {
            writer.write(data, off, Math.min(CHUNK, data.length() - off));
        }
    }

    private void writeBytes(PooledByteArrayOutputStream out) {
        for (int off = 0; off < bytes.length; off += CHUNK) {
            out.write(bytes, off, Math.min(CHUNK, bytes.length - off));
        }
    }

    @Benchmark
    public String charsChunked() {
        PooledStringWriter writer = new PooledStringWriter(recycler);
        try {
            writeChars(writer);
            return writer.getAndRecycle();
        } finally {
            writer.recycle();
        }
    }

    @Benchmark
    public byte[] bytesChunked() {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream(recycler);
        try {
            writeBytes(out);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }
}
