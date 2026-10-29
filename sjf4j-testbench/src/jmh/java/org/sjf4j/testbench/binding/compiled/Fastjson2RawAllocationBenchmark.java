package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Allocation and throughput comparison using the three production entry points:
 * native = Fastjson2 JSONReader.readObject(), compiled = @CompiledBinder,
 * runtime = Fastjson2Binder.readNode(Map.class).
 *
 * Each invocation includes parser construction and closing; fixture generation
 * and validation are excluded from the timed operation. Native JSONObject and
 * JSONArray are valid Map/List results, not conversions to be benchmarked away.
 * Run with -prof gc to compare gc.alloc.rate.norm (bytes per operation).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class Fastjson2RawAllocationBenchmark {

    @Param({"small", "wide", "deep"})
    public String shape;

    private final Fastjson2Binder binder = new Fastjson2Binder();
    private final JSONReader.Context nativeContext =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
    private final RawCompiledBinder compiled = CompiledInstances.create(RawCompiledBinder.class);

    private String json;
    private byte[] utf8;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        switch (shape) {
            case "small":
                json = "{\"id\":7,\"name\":\"Ada\",\"values\":[1,true,null]}";
                break;
            case "wide":
                json = createWideObject();
                break;
            case "deep":
                json = createDeepObject();
                break;
            default:
                throw new IllegalStateException(shape);
        }
        utf8 = json.getBytes(StandardCharsets.UTF_8);

        Map<String, Object> expected = compiled.readMap(json);
        verifyEquivalent(expected, compiled.readMapUtf8(utf8));
        verifyEquivalent(expected, nativeString());
        verifyEquivalent(expected, nativeUtf8());
        verifyEquivalent(expected, runtimeString());
        verifyEquivalent(expected, runtimeUtf8());
    }

    private static String createWideObject() {
        StringBuilder s = new StringBuilder(2048).append('{');
        for (int i = 0; i < 64; i++) {
            if (i != 0) s.append(',');
            s.append('"').append("field_").append(i).append("\":");
            switch (i % 4) {
                case 0:
                    s.append(i);
                    break;
                case 1:
                    s.append('"').append("name_").append(i).append('"');
                    break;
                case 2:
                    s.append("{\"n\":").append(i).append(",\"ok\":true}");
                    break;
                default:
                    s.append('[').append(i).append(",null,false]");
            }
        }
        return s.append('}').toString();
    }

    private static String createDeepObject() {
        StringBuilder s = new StringBuilder(512);
        for (int i = 0; i < 16; i++) s.append("{\"level\":").append(i).append(",\"child\":");
        s.append("{\"value\":[0,1,2,\"leaf\"]}");
        for (int i = 0; i < 16; i++) s.append('}');
        return s.toString();
    }

    private static void verifyEquivalent(Map<String, Object> expected, Object actual) {
        if (!(actual instanceof Map) || !expected.equals(actual)) {
            throw new IllegalStateException("Raw graph differs: " + actual);
        }
    }

    // Main comparison: always keep native / compiled / runtime together.
    @Benchmark
    public Object nativeString() {
        try (JSONReader reader = JSONReader.of(json, nativeContext)) {
            return reader.readObject();
        }
    }

    @Benchmark
    public Object compiledString() throws IOException {
        return compiled.readMap(json);
    }

    @Benchmark
    public Object runtimeString() {
        return binder.readNode(json, Map.class);
    }

    @Benchmark
    public Object nativeUtf8() {
        try (JSONReader reader = JSONReader.of(utf8, nativeContext)) {
            return reader.readObject();
        }
    }

    @Benchmark
    public Object compiledUtf8() throws IOException {
        return compiled.readMapUtf8(utf8);
    }

    @Benchmark
    public Object runtimeUtf8() {
        return binder.readNode(utf8, Map.class);
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface RawCompiledBinder {
        @ReadFrom Map<String, Object> readMap(String input) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] input) throws IOException;
    }
}
