package org.sjf4j.binding;

import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * The same 1K output with either an ordinary 1K strong cache or a previously
 * used 1M soft cache. A benchmark invocation includes checkout and recycle.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class SoftCacheSmallOutputBenchmark {
    @Param({"STRONG_1K", "SOFT_1M"})
    public String cacheKind;

    @Param({"ASCII", "UNICODE"})
    public String alphabet;

    private String text;
    private byte[] bytes;
    private BufferRecycler jacksonRecycler;

    @Setup(Level.Trial)
    public void prepare() throws IOException {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        recycler.takeLargeBytes(1);
        recycler.takeLargeChars(1);
        recycler.takeBytes();
        recycler.takeChars();

        text = ("ASCII".equals(alphabet) ? "abcd1234" : "中文测试").repeat(256).substring(0, 1024);
        bytes = new byte[1024];
        Arrays.fill(bytes, (byte) 0x61);

        if ("SOFT_1M".equals(cacheKind)) {
            recycler.recycleBytes(new byte[1024 * 1024]);
            recycler.recycleChars(new char[1024 * 1024]);
        } else {
            recycler.recycleBytes(new byte[1024]);
            recycler.recycleChars(new char[1024]);
        }

        jacksonRecycler = new BufferRecycler();
        SegmentedStringWriter jackson = new SegmentedStringWriter(jacksonRecycler);
        if ("SOFT_1M".equals(cacheKind)) {
            jackson.write("A".repeat(1024 * 1024));
        } else {
            jackson.write(text);
        }
        jackson.getAndClear();

        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write(text);
            if (!text.equals(writer.getAndRecycle())) throw new IllegalStateException("Invalid char result");
        } finally { writer.recycle(); }
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            output.write(bytes, 0, bytes.length);
            if (!Arrays.equals(bytes, output.getAndRecycle())) throw new IllegalStateException("Invalid byte result");
        } finally { output.recycle(); }
    }

    @Benchmark
    public String pooledChars() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write(text);
            return writer.getAndRecycle();
        } finally { writer.recycle(); }
    }

    @Benchmark
    public byte[] pooledBytes() {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try {
            output.write(bytes, 0, bytes.length);
            return output.getAndRecycle();
        } finally { output.recycle(); }
    }

    @Benchmark
    public String jacksonChars() throws IOException {
        SegmentedStringWriter writer = new SegmentedStringWriter(jacksonRecycler);
        writer.write(text);
        return writer.getAndClear();
    }
}
