package org.sjf4j.testbench.binding.handwritten;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.binding.FastStringWriter;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.binding.PooledStringWriter;
import org.sjf4j.binding.PooledStringWriterCap128;
import org.sjf4j.binding.PooledStringWriterCap256;
import org.sjf4j.binding.PooledByteArrayOutputStreamCap128;
import org.sjf4j.binding.PooledByteArrayOutputStreamCap256;
import org.sjf4j.binding.PooledStringWriterRef;
import org.sjf4j.binding.PooledStringWriterNoRetain;
import org.sjf4j.binding.PooledStringWriterColdGrow;
import org.sjf4j.binding.PooledStringWriterDeferredRetain;
import org.sjf4j.binding.PooledStringWriterRefLargeDouble;
import org.sjf4j.binding.PooledByteArrayOutputStreamLargeDouble;
import org.sjf4j.binding.PooledStringWriterRefRetain;
import org.sjf4j.binding.PooledStringWriterLargeExact;
import org.sjf4j.binding.PooledStringWriterLargeDouble;
import org.sjf4j.binding.PooledStringWriterFactory;
import org.sjf4j.binding.PooledStringWriterCharChecks;
import org.sjf4j.binding.PooledStringWriterStringChecks;
import org.sjf4j.binding.PooledStringWriterDirectResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Separates bare output-container cost from end-to-end native generator cost.
 * Each benchmark constructs the sink, writes output, materializes a detached
 * String or byte[], and releases the owned storage.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class OutputBufferBenchmark {

    private static final com.fasterxml.jackson.core.JsonFactory JACKSON2 =
            new com.fasterxml.jackson.core.JsonFactory();
    private static final tools.jackson.core.json.JsonFactory JACKSON3 =
            new tools.jackson.core.json.JsonFactory();
    private static final tools.jackson.core.ObjectWriteContext JACKSON3_CONTEXT =
            tools.jackson.core.ObjectWriteContext.empty();
    private static final Gson GSON = new Gson();

    @Param({"512", "8192", "20480"})
    public int payloadLength;

    @Param({"ASCII", "UNICODE"})
    public String alphabet;

    private String data;
    private byte[] bytes;

    @Setup(Level.Trial)
    public void prepare() throws IOException {
        String pattern = alphabet.equals("ASCII") ? "hello-1234\"\\\\\\n" : "中文测试例\"\\\\\\n";
        StringBuilder builder = new StringBuilder(payloadLength);
        while (builder.length() < payloadLength) builder.append(pattern);
        data = builder.substring(0, payloadLength);
        bytes = data.getBytes(StandardCharsets.UTF_8);

        // Verify every output sink produces the same bytes/chars.
        if (!data.equals(charsJdk()) || !data.equals(charsFast())
                || !data.equals(charsPooled())
                || !data.equals(charsHandwrittenPooled())
                || !data.equals(charsRefLargeDouble())
                || !data.equals(charsLargeExact()) || !data.equals(charsLargeDouble())
                || !data.equals(charsCap128()) || !data.equals(charsCap256())
                || !data.equals(charsJackson())
                || !data.equals(charsDirectResult()) || !data.equals(charsStringChecks())
                || !data.equals(charsCharChecks()) || !data.equals(charsFactory()))
            throw new IllegalStateException("Character output differs");
        if (!java.util.Arrays.equals(bytes, bytesJdk())
                || !java.util.Arrays.equals(bytes, bytesLargeDouble())
                || !java.util.Arrays.equals(bytes, bytesCap128())
                || !java.util.Arrays.equals(bytes, bytesCap256())
                || !java.util.Arrays.equals(bytes, bytesPooled())
                || !java.util.Arrays.equals(bytes, bytesJackson()))
            throw new IllegalStateException("Byte output differs");
        String expected = gsonJdk();
        if (!expected.equals(gsonFast()) || !expected.equals(gsonPooled()) || !expected.equals(gsonPooledRef())
                || !expected.equals(gsonHandwrittenPooled())
                || !expected.equals(jackson2StringJdk())
                || !expected.equals(jackson2StringPooled()) || !expected.equals(jackson2StringPooledRef())
                || !expected.equals(jackson2StringHandwrittenPooled())
                || !expected.equals(gsonRefLargeDouble())
                || !expected.equals(jackson2StringRefLargeDouble())
                || !expected.equals(gsonDeferredRetain())
                || !expected.equals(gsonColdGrow())
                || !expected.equals(gsonNoRetain()) || !expected.equals(gsonRefRetain())
                || !expected.equals(gsonLargeDouble())
                || !expected.equals(jackson2StringLargeDouble())
                || !expected.equals(jackson2StringCap128())
                || !expected.equals(jackson2StringCap256())
                || !expected.equals(jackson2StringSegmented())
                  || !expected.equals(gsonDirectResult()) || !expected.equals(jackson2StringDirectResult())  || !expected.equals(gsonStringChecks()) || !expected.equals(jackson2StringStringChecks())  || !expected.equals(gsonCharChecks()) || !expected.equals(jackson2StringCharChecks())  || !expected.equals(gsonFactory()) || !expected.equals(jackson2StringFactory()) || !expected.equals(jackson3StringPooled()))
            throw new IllegalStateException("JSON String output differs");
        byte[] expectedUtf8 = expected.getBytes(StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(expectedUtf8, jackson2BytesJdk())
                || !java.util.Arrays.equals(expectedUtf8, jackson2BytesLargeDouble())
                || !java.util.Arrays.equals(expectedUtf8, jackson2BytesCap128())
                || !java.util.Arrays.equals(expectedUtf8, jackson2BytesCap256())
                || !java.util.Arrays.equals(expectedUtf8, jackson2BytesPooled())
                || !java.util.Arrays.equals(expectedUtf8, jackson2BytesSegmented()))
            throw new IllegalStateException("JSON bytes output differs");
    }

    // Bare Writer cost; no JSON escaping or generator included.

    @Benchmark
    public String charsJdk() {
        StringWriter out = new StringWriter();
        out.write(data);
        return out.toString();
    }

    @Benchmark
    public String charsFast() {
        FastStringWriter out = new FastStringWriter();
        out.write(data);
        return out.toString();
    }

    @Benchmark
    public String charsPooled() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsHandwrittenPooled() {
        org.sjf4j.testbench.binding.handwritten.FastStringWriter out =
                org.sjf4j.testbench.binding.handwritten.FastStringWriter.acquire();
        try {
            out.write(data);
            return out.toStringAndRelease();
        } finally {
            out.release();
        }
    }

    private static final int CHUNK_SIZE = 64;

    @Benchmark
    public String charsChunksPooled() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            for (int pos = 0; pos < data.length(); pos += CHUNK_SIZE) {
                out.write(data, pos, Math.min(CHUNK_SIZE, data.length() - pos));
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsChunksHandwritten() {
        org.sjf4j.testbench.binding.handwritten.FastStringWriter out =
                org.sjf4j.testbench.binding.handwritten.FastStringWriter.acquire();
        try {
            for (int pos = 0; pos < data.length(); pos += CHUNK_SIZE) {
                out.write(data, pos, Math.min(CHUNK_SIZE, data.length() - pos));
            }
            return out.toStringAndRelease();
        } finally {
            out.release();
        }
    }

    @Benchmark
    public String charsArrayChunksPooled() {
        PooledStringWriter out = new PooledStringWriter();
        char[] src = data.toCharArray();
        try {
            for (int pos = 0; pos < src.length; pos += CHUNK_SIZE) {
                out.write(src, pos, Math.min(CHUNK_SIZE, src.length - pos));
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsArrayChunksHandwritten() {
        org.sjf4j.testbench.binding.handwritten.FastStringWriter out =
                org.sjf4j.testbench.binding.handwritten.FastStringWriter.acquire();
        char[] src = data.toCharArray();
        try {
            for (int pos = 0; pos < src.length; pos += CHUNK_SIZE) {
                out.write(src, pos, Math.min(CHUNK_SIZE, src.length - pos));
            }
            return out.toStringAndRelease();
        } finally {
            out.release();
        }
    }

    @Benchmark
    public String charsJackson() throws IOException {
        BufferRecycler recycler = JACKSON2._getBufferRecycler();
        try (SegmentedStringWriter out = new SegmentedStringWriter(recycler)) {
            out.write(data);
            return out.getAndClear();
        } finally {
            recycler.releaseToPool();
        }
    }

    // Bare OutputStream cost.

    @Benchmark
    public byte[] bytesJdk() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes);
        return out.toByteArray();
    }

    @Benchmark
    public byte[] bytesPooled() {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
        try {
            out.write(bytes, 0, bytes.length);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public byte[] bytesJackson() throws IOException {
        BufferRecycler recycler = JACKSON2._getBufferRecycler();
        ByteArrayBuilder out = new ByteArrayBuilder(recycler);
        try {
            out.write(bytes);
            return out.toByteArray();
        } finally {
            out.release();
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public String charsPooledRef() {
        PooledStringWriterRef out = new PooledStringWriterRef();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    // Gson: a backend whose serializer writes through generic Writer.

    @Benchmark
    public String gsonJdk() throws IOException {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = GSON.newJsonWriter(out)) {
            writer.value(data);
        }
        return out.toString();
    }

    @Benchmark
    public String gsonFast() throws IOException {
        FastStringWriter out = new FastStringWriter();
        try (JsonWriter writer = GSON.newJsonWriter(out)) {
            writer.value(data);
        }
        return out.toString();
    }

    @Benchmark
    public String gsonPooled() throws IOException {
        PooledStringWriter out = new PooledStringWriter();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonHandwrittenPooled() throws IOException {
        org.sjf4j.testbench.binding.handwritten.FastStringWriter out =
                org.sjf4j.testbench.binding.handwritten.FastStringWriter.acquire();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.toStringAndRelease();
        } finally {
            out.release();
        }
    }

    @Benchmark
    public String gsonPooledRef() throws IOException {
        PooledStringWriterRef out = new PooledStringWriterRef();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    // Jackson 2: preserve the native generator, change only the output target.

    @Benchmark
    public String jackson2StringJdk() throws IOException {
        StringWriter out = new StringWriter();
        try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
            writer.writeString(data);
        }
        return out.toString();
    }

    @Benchmark
    public String jackson2StringPooled() throws IOException {
        PooledStringWriter out = new PooledStringWriter();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringPooledRef() throws IOException {
        PooledStringWriterRef out = new PooledStringWriterRef();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringHandwrittenPooled() throws IOException {
        org.sjf4j.testbench.binding.handwritten.FastStringWriter out =
                org.sjf4j.testbench.binding.handwritten.FastStringWriter.acquire();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.toStringAndRelease();
        } finally {
            out.release();
        }
    }

    @Benchmark
    public String jackson2StringSegmented() throws IOException {
        BufferRecycler recycler = JACKSON2._getBufferRecycler();
        try (SegmentedStringWriter out = new SegmentedStringWriter(recycler)) {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.getAndClear();
        } finally {
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public byte[] jackson2BytesJdk() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator writer = JACKSON2.createGenerator(out, JsonEncoding.UTF8)) {
            writer.writeString(data);
        }
        return out.toByteArray();
    }

    @Benchmark
    public byte[] jackson2BytesPooled() throws IOException {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out, JsonEncoding.UTF8)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public byte[] jackson2BytesSegmented() throws IOException {
        BufferRecycler recycler = JACKSON2._getBufferRecycler();
        ByteArrayBuilder out = new ByteArrayBuilder(recycler);
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out, JsonEncoding.UTF8)) {
                writer.writeString(data);
            }
            return out.toByteArray();
        } finally {
            out.release();
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public String charsDirectResult() {
        PooledStringWriterDirectResult out = new PooledStringWriterDirectResult();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonDirectResult() throws IOException {
        PooledStringWriterDirectResult out = new PooledStringWriterDirectResult();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) { writer.value(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringDirectResult() throws IOException {
        PooledStringWriterDirectResult out = new PooledStringWriterDirectResult();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsStringChecks() {
        PooledStringWriterStringChecks out = new PooledStringWriterStringChecks();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonStringChecks() throws IOException {
        PooledStringWriterStringChecks out = new PooledStringWriterStringChecks();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) { writer.value(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringStringChecks() throws IOException {
        PooledStringWriterStringChecks out = new PooledStringWriterStringChecks();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsCharChecks() {
        PooledStringWriterCharChecks out = new PooledStringWriterCharChecks();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonCharChecks() throws IOException {
        PooledStringWriterCharChecks out = new PooledStringWriterCharChecks();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) { writer.value(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringCharChecks() throws IOException {
        PooledStringWriterCharChecks out = new PooledStringWriterCharChecks();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsFactory() {
        PooledStringWriterFactory out = PooledStringWriterFactory.acquire();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonFactory() throws IOException {
        PooledStringWriterFactory out = PooledStringWriterFactory.acquire();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) { writer.value(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringFactory() throws IOException {
        PooledStringWriterFactory out = PooledStringWriterFactory.acquire();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    // Jackson 3 to catch a different recycler policy and generator behavior.

    @Benchmark
    public String jackson3StringPooled() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            try (tools.jackson.core.JsonGenerator writer =
                    JACKSON3.createGenerator(JACKSON3_CONTEXT, out)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }
    @Benchmark
    public String gsonNoRetain() throws IOException {
        PooledStringWriterNoRetain out = new PooledStringWriterNoRetain();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonRefRetain() throws IOException {
        PooledStringWriterRefRetain out = new PooledStringWriterRefRetain();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsLargeExact() throws IOException {
        PooledStringWriterLargeExact out = new PooledStringWriterLargeExact();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonLargeExact() throws IOException {
        PooledStringWriterLargeExact out = new PooledStringWriterLargeExact();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringLargeExact() throws IOException {
        PooledStringWriterLargeExact out = new PooledStringWriterLargeExact();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String charsLargeDouble() throws IOException {
        PooledStringWriterLargeDouble out = new PooledStringWriterLargeDouble();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonLargeDouble() throws IOException {
        PooledStringWriterLargeDouble out = new PooledStringWriterLargeDouble();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringLargeDouble() throws IOException {
        PooledStringWriterLargeDouble out = new PooledStringWriterLargeDouble();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String gsonColdGrow() throws IOException {
        PooledStringWriterColdGrow out = new PooledStringWriterColdGrow();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringColdGrow() throws IOException {
        PooledStringWriterColdGrow out = new PooledStringWriterColdGrow();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }
    @Benchmark
    public String gsonDeferredRetain() throws IOException {
        PooledStringWriterDeferredRetain out = new PooledStringWriterDeferredRetain();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) {
                writer.value(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }

    @Benchmark
    public String jackson2StringDeferredRetain() throws IOException {
        PooledStringWriterDeferredRetain out = new PooledStringWriterDeferredRetain();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally {
            out.recycle();
        }
    }
    @Benchmark
    public String gsonRefLargeDouble() throws IOException {
        PooledStringWriterRefLargeDouble out = new PooledStringWriterRefLargeDouble();
        try {
            try (JsonWriter writer = GSON.newJsonWriter(out)) { writer.value(data); }
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public String jackson2StringRefLargeDouble() throws IOException {
        PooledStringWriterRefLargeDouble out = new PooledStringWriterRefLargeDouble();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public String charsRefLargeDouble() {
        PooledStringWriterRefLargeDouble out = new PooledStringWriterRefLargeDouble();
        try {
            out.write(data);
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public byte[] bytesLargeDouble() {
        PooledByteArrayOutputStreamLargeDouble out = new PooledByteArrayOutputStreamLargeDouble();
        try {
            out.write(bytes, 0, bytes.length);
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public byte[] jackson2BytesLargeDouble() throws IOException {
        PooledByteArrayOutputStreamLargeDouble out = new PooledByteArrayOutputStreamLargeDouble();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out, JsonEncoding.UTF8)) {
                writer.writeString(data);
            }
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public String charsCap128() {
        PooledStringWriterCap128 out = new PooledStringWriterCap128();
        try { out.write(data); return out.getAndRecycle(); }
        finally { out.recycle(); }
    }

    @Benchmark
    public String jackson2StringCap128() throws IOException {
        PooledStringWriterCap128 out = new PooledStringWriterCap128();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public byte[] bytesCap128() {
        PooledByteArrayOutputStreamCap128 out = new PooledByteArrayOutputStreamCap128();
        try { out.write(bytes, 0, bytes.length); return out.getAndRecycle(); }
        finally { out.recycle(); }
    }

    @Benchmark
    public byte[] jackson2BytesCap128() throws IOException {
        PooledByteArrayOutputStreamCap128 out = new PooledByteArrayOutputStreamCap128();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out, JsonEncoding.UTF8)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }


    @Benchmark
    public String charsCap256() {
        PooledStringWriterCap256 out = new PooledStringWriterCap256();
        try { out.write(data); return out.getAndRecycle(); }
        finally { out.recycle(); }
    }

    @Benchmark
    public String jackson2StringCap256() throws IOException {
        PooledStringWriterCap256 out = new PooledStringWriterCap256();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

    @Benchmark
    public byte[] bytesCap256() {
        PooledByteArrayOutputStreamCap256 out = new PooledByteArrayOutputStreamCap256();
        try { out.write(bytes, 0, bytes.length); return out.getAndRecycle(); }
        finally { out.recycle(); }
    }

    @Benchmark
    public byte[] jackson2BytesCap256() throws IOException {
        PooledByteArrayOutputStreamCap256 out = new PooledByteArrayOutputStreamCap256();
        try {
            try (JsonGenerator writer = JACKSON2.createGenerator(out, JsonEncoding.UTF8)) { writer.writeString(data); }
            return out.getAndRecycle();
        } finally { out.recycle(); }
    }

}
