package org.sjf4j.testbench.binding.runtime;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.util.FastUuid;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** End-to-end UUID streaming versus pre-streaming string conversion and backend native. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StreamingUuidBenchmark {
    private static final UUID UUID_VALUE = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String JSON_VALUE = "\"" + UUID_VALUE + "\"";
    private static final byte[] JSON_UTF8 = JSON_VALUE.getBytes(StandardCharsets.UTF_8);
    private static final Fastjson2Binder RUNTIME = new Fastjson2Binder();

    @Benchmark
    public Object runtimeReadString() {
        return RUNTIME.readNode(JSON_VALUE, UUID.class);
    }

    @Benchmark
    public Object runtimeReadUtf8() {
        return RUNTIME.readNode(JSON_UTF8, UUID.class);
    }

    @Benchmark
    public Object originalReadString() {
        try (JSONReader reader = JSONReader.of(JSON_VALUE)) {
            return FastUuid.parseUUID(reader.readString());
        }
    }

    @Benchmark
    public Object originalReadUtf8() {
        try (JSONReader reader = JSONReader.of(JSON_UTF8)) {
            return FastUuid.parseUUID(reader.readString());
        }
    }

    @Benchmark
    public Object nativeReadString() {
        try (JSONReader reader = JSONReader.of(JSON_VALUE)) {
            return reader.readUUID();
        }
    }

    @Benchmark
    public Object nativeReadUtf8() {
        try (JSONReader reader = JSONReader.of(JSON_UTF8)) {
            return reader.readUUID();
        }
    }

    @Benchmark
    public String runtimeWriteString() {
        return RUNTIME.writeNodeAsString(UUID_VALUE);
    }

    @Benchmark
    public byte[] runtimeWriteUtf8() {
        return RUNTIME.writeNodeAsBytes(UUID_VALUE);
    }

    @Benchmark
    public String originalWriteString() {
        try (JSONWriter writer = JSONWriter.of()) {
            writer.writeString(FastUuid.toString(UUID_VALUE));
            return writer.toString();
        }
    }

    @Benchmark
    public byte[] originalWriteUtf8() {
        try (JSONWriter writer = JSONWriter.ofUTF8()) {
            writer.writeString(FastUuid.toString(UUID_VALUE));
            return writer.getBytes();
        }
    }

    @Benchmark
    public String nativeWriteString() {
        try (JSONWriter writer = JSONWriter.of()) {
            writer.writeUUID(UUID_VALUE);
            return writer.toString();
        }
    }

    @Benchmark
    public byte[] nativeWriteUtf8() {
        try (JSONWriter writer = JSONWriter.ofUTF8()) {
            writer.writeUUID(UUID_VALUE);
            return writer.getBytes();
        }
    }
}
