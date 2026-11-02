package org.sjf4j.testbench.binding.compiled;

import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Isolated real-path A/B for 0/1/2-member LinkedHashMap specialization.
 * Run twice in independent JVM forks with -Dsjf4j.test.smallRawMap=false/true.
 * Both sides use the same generated CompiledBinder, input, and backend.
 * Report per-shape latency and -prof gc normalized allocation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 350, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class RawSmallMapABBenchmark {

    @Param({"fastjson2", "jackson2", "jackson3", "gson", "jsonp", "simple"})
    public String backend;

    @Param({"empty", "one", "two", "three", "six", "nested", "user"})
    public String shape;

    @Param({"string", "utf8"})
    public String encoding;

    private String json;
    private byte[] data;
    private int expectedSize;

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Reader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Reader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2Reader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonReader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpReader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleReader {
        @ReadFrom Map<String, Object> read(String json) throws IOException;
        @ReadFrom Map<String, Object> readBytes(byte[] json) throws IOException;
    }

    private static final Jackson2Reader C_JACKSON2 = CompiledInstances.create(Jackson2Reader.class);
    private static final Jackson3Reader C_JACKSON3 = CompiledInstances.create(Jackson3Reader.class);
    private static final Fastjson2Reader C_FASTJSON2 = CompiledInstances.create(Fastjson2Reader.class);
    private static final GsonReader C_GSON = CompiledInstances.create(GsonReader.class);
    private static final JsonpReader C_JSONP = CompiledInstances.create(JsonpReader.class);
    private static final SimpleReader C_SIMPLE = CompiledInstances.create(SimpleReader.class);

    @Setup(Level.Trial)
    public void setup() throws IOException {
        switch (shape) {
            case "empty":
                json = "{}";
                expectedSize = 0;
                break;
            case "one":
                json = "{\"a\":1}";
                expectedSize = 1;
                break;
            case "two":
                json = "{\"a\":1,\"b\":null}";
                expectedSize = 2;
                break;
            case "three":
                json = "{\"a\":1,\"b\":null,\"c\":\"Ada\"}";
                expectedSize = 3;
                break;
            case "six":
                json = "{\"a\":1,\"b\":null,\"c\":\"Ada\",\"d\":true,\"e\":[1,2],\"f\":{\"nested\":1}}";
                expectedSize = 6;
                break;
            case "nested":
                json = "{\"outer\":{\"a\":1},\"items\":[{\"b\":2},{\"c\":3}]}";
                expectedSize = 2;
                break;
            case "user":
                json = User.JSON_DATA;
                expectedSize = 23;
                break;
            default:
                throw new IllegalArgumentException(shape);
        }
        data = json.getBytes(StandardCharsets.UTF_8);
        Map<String, Object> result = readCompiled();
        if (result == null || result.size() != expectedSize) {
            throw new AssertionError(backend + "/" + shape + ": expected " + expectedSize + ", got " + result);
        }
        if ("nested".equals(shape)) {
            if (!(((Map<?, ?>) result.get("outer")).get("a") instanceof Number)
                    || ((java.util.List<?>) result.get("items")).size() != 2) {
                throw new AssertionError("nested raw graph incorrect: " + result);
            }
        }
    }

    @Benchmark
    public Map<String, Object> readCompiled() throws IOException {
        boolean bytes = "utf8".equals(encoding);
        switch (backend) {
            case "fastjson2": return bytes ? C_FASTJSON2.readBytes(data) : C_FASTJSON2.read(json);
            case "jackson2": return bytes ? C_JACKSON2.readBytes(data) : C_JACKSON2.read(json);
            case "jackson3": return bytes ? C_JACKSON3.readBytes(data) : C_JACKSON3.read(json);
            case "gson": return bytes ? C_GSON.readBytes(data) : C_GSON.read(json);
            case "jsonp": return bytes ? C_JSONP.readBytes(data) : C_JSONP.read(json);
            case "simple": return bytes ? C_SIMPLE.readBytes(data) : C_SIMPLE.read(json);
            default: throw new IllegalArgumentException(backend);
        }
    }
}
