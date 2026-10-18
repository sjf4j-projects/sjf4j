package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import lombok.Data;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Reader;
import org.sjf4j.binding.StreamingIO;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.testbench.model.Address;
import org.sjf4j.testbench.model.Friend;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Isolate URI ValueCodec overhead with an identical JSON payload.
 *
 * Both 23-property POJOs use String updatedAt: no LocalDateTime decoding,
 * no backend-specific native date parser. The only type difference is
 * website: URI (compiled codec) versus String (raw).
 *
 * Scalar variants separate StreamingIO, cached ValueInfo and direct built-in
 * ValueCodec calls. All measured methods create and close a parser.
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
    private static final String URI_JSON = "\"https://example.test/users/42\"";
    private static final URI EXPECTED_URI = URI.create("https://example.test/users/42");

    private static final JSONReader.Context NATIVE_CONTEXT =
            JSONFactory.createReadContext(new ObjectReaderProvider(),
                    JSONReader.Feature.UseDoubleForDecimals);
    private static final Fastjson2Binder BINDER =
            new Fastjson2Binder(NATIVE_CONTEXT, JSONFactory.createWriteContext());
    private static final ValueInfo URI_INFO =
            TypeRegistry.registerTypeInfo(URI.class).requireValueInfo(null);
    private static final UriBinder URI_COMPILED = CompiledInstances.global(UriBinder.class);
    private static final RawBinder RAW_COMPILED = CompiledInstances.global(RawBinder.class);

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface UriBinder {
        @ReadFrom UriUser readString(String json) throws IOException;
        @ReadFrom UriUser readUtf8(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface RawBinder {
        @ReadFrom RawUser readString(String json) throws IOException;
        @ReadFrom RawUser readUtf8(byte[] json) throws IOException;
    }

    @Data
    public static class UriUser {
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
        private URI website;
        private String department;
        private Address address;
        private List<String> tags;
        private List<Friend> friends;
    }

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
        UriUser nativeUri = (UriUser) native_uri_string();
        if (!nativeUri.equals(compiled_uri_string())
                || !nativeUri.equals(native_uri_utf8())
                || !nativeUri.equals(compiled_uri_utf8())) {
            throw new AssertionError("URI POJO read mismatch");
        }
        RawUser nativeRaw = (RawUser) native_raw_string();
        if (!nativeRaw.equals(compiled_raw_string())
                || !nativeRaw.equals(native_raw_utf8())
                || !nativeRaw.equals(compiled_raw_utf8())) {
            throw new AssertionError("Raw POJO read mismatch");
        }
        if (!nativeUri.getUpdatedAt().equals(nativeRaw.getUpdatedAt())
                || !nativeUri.getWebsite().toString().equals(nativeRaw.getWebsite())) {
            throw new AssertionError("URI and raw input mismatch");
        }
        if (!EXPECTED_URI.equals(native_scalar())
                || !EXPECTED_URI.equals(direct_codec_scalar())
                || !EXPECTED_URI.equals(value_info_scalar())
                || !EXPECTED_URI.equals(streaming_io_scalar())) {
            throw new AssertionError("Scalar URI read mismatch");
        }
    }

    @Benchmark
    public Object native_uri_string() {
        try (JSONReader reader = JSONReader.of(JSON, NATIVE_CONTEXT)) {
            return reader.read(UriUser.class);
        }
    }

    @Benchmark
    public Object compiled_uri_string() throws IOException {
        return URI_COMPILED.readString(JSON);
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
    public Object native_uri_utf8() {
        try (JSONReader reader = JSONReader.of(UTF8, NATIVE_CONTEXT)) {
            return reader.read(UriUser.class);
        }
    }

    @Benchmark
    public Object compiled_uri_utf8() throws IOException {
        return URI_COMPILED.readUtf8(UTF8);
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

    @Benchmark
    public Object native_scalar() {
        try (JSONReader reader = JSONReader.of(URI_JSON, NATIVE_CONTEXT)) {
            return reader.read(URI.class);
        }
    }

    @Benchmark
    public Object direct_codec_scalar() throws IOException {
        try (Fastjson2Reader reader = BINDER.createReader(URI_JSON)) {
            return ValueCodec.URI_CODEC.rawToValue(reader.readString());
        }
    }

    @Benchmark
    public Object value_info_scalar() throws IOException {
        try (Fastjson2Reader reader = BINDER.createReader(URI_JSON)) {
            return URI_INFO.rawToValue(reader.readString());
        }
    }

    @Benchmark
    public Object streaming_io_scalar() throws IOException {
        try (Fastjson2Reader reader = BINDER.createReader(URI_JSON)) {
            return StreamingIO.readValueCodec(reader, URI_INFO, RuntimeContext.EMPTY);
        }
    }
}
