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

    @Param({"512", "8192"})
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
                || !data.equals(charsPooled()) || !data.equals(charsHandwrittenPooled())
                || !data.equals(charsJackson()))
            throw new IllegalStateException("Character output differs");
        if (!java.util.Arrays.equals(bytes, bytesJdk())
                || !java.util.Arrays.equals(bytes, bytesPooled())
                || !java.util.Arrays.equals(bytes, bytesJackson()))
            throw new IllegalStateException("Byte output differs");
        String expected = gsonJdk();
        if (!expected.equals(gsonFast()) || !expected.equals(gsonPooled())
                || !expected.equals(gsonHandwrittenPooled())
                || !expected.equals(jackson2StringJdk())
                || !expected.equals(jackson2StringPooled())
                || !expected.equals(jackson2StringHandwrittenPooled())
                || !expected.equals(jackson2StringSegmented())
                || !expected.equals(jackson3StringPooled()))
            throw new IllegalStateException("JSON String output differs");
        byte[] expectedUtf8 = expected.getBytes(StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(expectedUtf8, jackson2BytesJdk())
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
}
