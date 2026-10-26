package org.sjf4j.testbench.binding;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.binding.PooledStringWriter;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import java.io.StringWriter;
import java.io.IOException;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Large ASCII String output may be significantly slower due to JDK Compact Strings.
 * Converting a large char[] to String requires full Latin-1 compression,
 * while Unicode content typically falls back to the faster UTF-16 path.
 *
 * Disabling Compact Strings (-XX:-CompactStrings) eliminates this slowdown,
 * but is not recommended as a general JVM optimization.
 */

/**
 * Comparable output-sink benchmark: SJF4J versus JDK and Jackson2.
 * Measures allocation and completion for full writes and 256-element chunks.
 * Complete large String writes in SJF4J can return the original String
 * (zero-copy); compare chunked writes to measure materialization costs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 7, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class PooledOutputBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{PooledOutputBenchmark.class.getName()});
    }

    private static final int CHUNK_SIZE = 256;
    @Param({"1024", "65530", "307208", "1038576"})
    public int payloadLength;
    @Param({"ASCII", "UNICODE"})
    public String alphabet;
    private String data;
    private byte[] bytes;
    private BufferRecycler jackson2Recycler;

    @Setup(Level.Trial)
    public void prepare() throws IOException {
        String pattern = "ASCII".equals(alphabet) ? "hello-1234" : "中文测试例";
        data = pattern.repeat((payloadLength + pattern.length() - 1) / pattern.length())
                .substring(0, payloadLength);
        bytes = data.getBytes(StandardCharsets.UTF_8);
        jackson2Recycler = new BufferRecycler();
        SegmentedStringWriter jackson = new SegmentedStringWriter(jackson2Recycler);
        jackson.write(data);
        if (!data.equals(jackson.getAndClear()))
            throw new IllegalStateException("Invalid Jackson2 SegmentedStringWriter result");
        PooledStringWriter chars = new PooledStringWriter();
        try {
            chars.write(data);
            if (!data.equals(chars.getAndRecycle()))
                throw new IllegalStateException("Invalid pooled String result");
        } finally {
            chars.recycle();
        }
        PooledByteArrayOutputStream sink = new PooledByteArrayOutputStream();
        try {
            sink.write(bytes, 0, bytes.length);
            if (!Arrays.equals(bytes, sink.getAndRecycle()))
                throw new IllegalStateException("Invalid pooled bytes result");
        } finally {
            sink.recycle();
        }
        if (!Arrays.equals(bytes, bytesJackson2()))
            throw new IllegalStateException("Invalid Jackson2 byte output");
    }

    @Benchmark
    public String charsPooled() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public String charsJdk() {
        StringWriter out = new StringWriter();
        out.write(data);
        return out.toString();
    }

    @Benchmark
    public String charsJackson2() throws IOException {
        SegmentedStringWriter out = new SegmentedStringWriter(jackson2Recycler);
        out.write(data);
        return out.getAndClear();
    }

    @Benchmark
    public byte[] bytesPooled() {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
        try {
            out.write(bytes, 0, bytes.length);
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public byte[] bytesJdk() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes, 0, bytes.length);
        return out.toByteArray();
    }

    @Benchmark
    public byte[] bytesJackson2() throws IOException {
        ByteArrayBuilder out = new ByteArrayBuilder(jackson2Recycler);
        try {
            out.write(bytes, 0, bytes.length);
            return out.toByteArray();
        } finally {
            out.release();
        }
    }

    @Benchmark
    public String charsChunksPooled() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            for (int i = 0; i < data.length(); i += CHUNK_SIZE)
                out.write(data, i, Math.min(CHUNK_SIZE, data.length() - i));
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public String charsChunksJdk() {
        StringWriter out = new StringWriter();
        for (int i = 0; i < data.length(); i += CHUNK_SIZE)
            out.write(data, i, Math.min(CHUNK_SIZE, data.length() - i));
        return out.toString();
    }

    @Benchmark
    public String charsChunksJackson2() throws IOException {
        SegmentedStringWriter out = new SegmentedStringWriter(jackson2Recycler);
        for (int i = 0; i < data.length(); i += CHUNK_SIZE)
            out.write(data, i, Math.min(CHUNK_SIZE, data.length() - i));
        return out.getAndClear();
    }

    @Benchmark
    public byte[] bytesChunksPooled() {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
        try {
            for (int i = 0; i < bytes.length; i += CHUNK_SIZE)
                out.write(bytes, i, Math.min(CHUNK_SIZE, bytes.length - i));
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public byte[] bytesChunksJdk() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i += CHUNK_SIZE)
            out.write(bytes, i, Math.min(CHUNK_SIZE, bytes.length - i));
        return out.toByteArray();
    }

    @Benchmark
    public byte[] bytesChunksJackson2() throws IOException {
        ByteArrayBuilder out = new ByteArrayBuilder(jackson2Recycler);
        try {
            for (int i = 0; i < bytes.length; i += CHUNK_SIZE)
                out.write(bytes, i, Math.min(CHUNK_SIZE, bytes.length - i));
            return out.toByteArray();
        } finally {
            out.release();
        }
    }

}
