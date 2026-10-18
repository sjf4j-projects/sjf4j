package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import lombok.Data;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.testbench.binding.handwritten.Fastjson2RawHandReader;
import org.sjf4j.testbench.binding.handwritten.Fastjson2RawNativeDateReader;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A/B: identical JSON bytes and property graph with/without ValueCodec decoding.
 * RawUser differs from User ONLY in updatedAt (String vs LocalDateTime)
 * and website (String vs URI). All benchmarks include parser construction.
 *
 * A handwritten raw-name reader provides the upper bound for a compiled
 * implementation that directly invokes the built-in codecs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 7, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class Fastjson2CodecReadProbeBenchmark {

    private static final String JSON = CompiledReadBenchmark.JSON_DATA;
    private static final byte[] UTF8 = JSON.getBytes(StandardCharsets.UTF_8);
    private static final JSONReader.Context NATIVE_CONTEXT =
            JSONFactory.createReadContext(new ObjectReaderProvider(),
                    JSONReader.Feature.UseDoubleForDecimals);

    private static final CompiledReadBenchmark.Fastjson2CompiledBinder CODEC_COMPILED =
            CompiledInstances.global(CompiledReadBenchmark.Fastjson2CompiledBinder.class);
    private static final RawBinder RAW_COMPILED = CompiledInstances.global(RawBinder.class);

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface RawBinder {
        @ReadFrom RawUser readString(String json) throws IOException;
        @ReadFrom RawUser readUtf8(byte[] json) throws IOException;
    }

    /** Exact same property graph as User, but retain the two codec raw strings. */
    @Data
    public static class RawUser {
        private long id;
        private long createdAt;
        private String updatedAt;
        private long reputation;
        private int loginCount;
        private int age;
        private boolean active;
        private boolean verified;
        private boolean admin;
        private boolean suspended;
        private double score;
        private double latitude;
        private double longitude;
        private String username;
        private String email;
        private String displayName;
        private String passwordHash;
        private String bio;
        private String website;
        private String department;
        private Address address;
        private List<String> tags;
        private List<Friend> friends;
    }

    @Setup(Level.Trial)
    public void checkResults() throws IOException {
        User nativeCodec = (User) native_codec_string();
        if (!nativeCodec.equals(compiled_codec_string())
                || !nativeCodec.equals(handwritten_codec_string())
                || !nativeCodec.equals(handwritten_native_date_string())
                || !nativeCodec.equals(handwritten_native_date_utf8())
                || !nativeCodec.equals(native_codec_utf8())
                || !nativeCodec.equals(compiled_codec_utf8())) {
            throw new AssertionError("Fastjson2 ValueCodec POJO result mismatch");
        }
        RawUser nativeRaw = (RawUser) native_raw_string();
        if (!nativeRaw.equals(compiled_raw_string())
                || !nativeRaw.equals(native_raw_utf8())
                || !nativeRaw.equals(compiled_raw_utf8())) {
            throw new AssertionError("Fastjson2 RawUser POJO result mismatch");
        }
    }

    @Benchmark
    public Object native_codec_string() {
        try (JSONReader reader = JSONReader.of(JSON, NATIVE_CONTEXT)) {
            return reader.read(User.class);
        }
    }

    @Benchmark
    public Object compiled_codec_string() throws IOException {
        return CODEC_COMPILED.readPojo(JSON);
    }

    @Benchmark
    public Object handwritten_codec_string() {
        try (JSONReader reader = JSONReader.of(JSON, NATIVE_CONTEXT)) {
            return Fastjson2RawHandReader.readUser(reader);
        }
    }

    @Benchmark
    public Object handwritten_native_date_string() {
        try (JSONReader reader = JSONReader.of(JSON, NATIVE_CONTEXT)) {
            return Fastjson2RawNativeDateReader.readUser(reader);
        }
    }

    @Benchmark
    public Object handwritten_native_date_utf8() {
        try (JSONReader reader = JSONReader.of(UTF8, NATIVE_CONTEXT)) {
            return Fastjson2RawNativeDateReader.readUser(reader);
        }
    }

    @Benchmark
    public Object native_raw_string() {
        try (JSONReader reader = JSONReader.of(JSON, NATIVE_CONTEXT)) {
            return reader.read(RawUser.class);
        }
    }

    @Benchmark
    public Object compiled_raw_string() throws IOException {
        return RAW_COMPILED.readString(JSON);
    }

    @Benchmark
    public Object native_codec_utf8() {
        try (JSONReader reader = JSONReader.of(UTF8, NATIVE_CONTEXT)) {
            return reader.read(User.class);
        }
    }

    @Benchmark
    public Object compiled_codec_utf8() throws IOException {
        return CODEC_COMPILED.readPojoUtf8(UTF8);
    }

    @Benchmark
    public Object native_raw_utf8() {
        try (JSONReader reader = JSONReader.of(UTF8, NATIVE_CONTEXT)) {
            return reader.read(RawUser.class);
        }
    }

    @Benchmark
    public Object compiled_raw_utf8() throws IOException {
        return RAW_COMPILED.readUtf8(UTF8);
    }
}
