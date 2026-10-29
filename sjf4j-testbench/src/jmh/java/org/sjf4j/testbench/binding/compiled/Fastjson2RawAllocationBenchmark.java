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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Allocation and throughput comparison using the three production entry points:
 * native = Fastjson2 JSONReader.read(Map.class), compiled = @CompiledBinder,
 * runtime = Fastjson2Binder.readNode(Map.class).
 *
 * Tiny fixtures isolate root parsing overhead; larger fixtures detect regression beyond the two-entry fast path.
 * Each invocation includes parser construction and closing; fixture generation
 * and validation are excluded from the timed operation. The native reader is
 * measured without converting its Map implementation to a different class.
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

    @Param({"empty", "one", "two", "small", "five", "six", "wide", "deep"})
    public String shape;

    private final Fastjson2Binder binder = new Fastjson2Binder();
    private final JSONReader.Context nativeContext =
            JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
    private final RawCompiledBinder compiled = CompiledInstances.create(RawCompiledBinder.class);

    private String json;
    private byte[] utf8;
    private String arrayJson;
    private byte[] arrayUtf8;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        switch (shape) {
            case "empty":
                json = "{}";
                break;
            case "one":
                json = "{\"id\":7}";
                break;
            case "two":
                json = "{\"id\":7,\"name\":\"Ada\"}";
                break;
            case "small":
                json = "{\"id\":7,\"name\":\"Ada\",\"values\":[1,true,null]}";
                break;
            case "five":
                json = createSizedObject(5);
                break;
            case "six":
                json = createSizedObject(6);
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
        arrayJson = createSizedArray(shape.equals("empty") ? 0 : shape.equals("one") ? 1
                : shape.equals("two") ? 2 : shape.equals("small") ? 3
                : shape.equals("five") ? 5 : shape.equals("six") ? 6
                : shape.equals("wide") ? 64 : 16);
        arrayUtf8 = arrayJson.getBytes(StandardCharsets.UTF_8);

        Map<String, Object> expected = compiled.readMap(json);
        verifyEquivalent(expected, compiled.readMapUtf8(utf8));
        verifyEquivalent(expected, mapNativeString());
        verifyEquivalent(expected, mapNativeUtf8());
        verifyEquivalent(expected, mapRuntimeString());
        verifyEquivalent(expected, mapRuntimeUtf8());

        List<Object> expectedList = compiled.readList(arrayJson);
        verifyEquivalent(expectedList, compiled.readListUtf8(arrayUtf8));
        verifyEquivalent(expectedList, listNativeString());
        verifyEquivalent(expectedList, listNativeUtf8());
        verifyEquivalent(expectedList, listRuntimeString());
        verifyEquivalent(expectedList, listRuntimeUtf8());
    }

    private static String createSizedObject(int count) {
        StringBuilder s = new StringBuilder(128).append('{');
        for (int i = 0; i < count; i++) {
            if (i != 0) s.append(',');
            s.append('"').append("k").append(i).append("\":").append(i);
        }
        return s.append('}').toString();
    }

    private static String createSizedArray(int count) {
        StringBuilder s = new StringBuilder(128).append('[');
        for (int i = 0; i < count; i++) {
            if (i != 0) s.append(',');
            switch (i % 4) {
                case 0: s.append(i); break;
                case 1: s.append("null"); break;
                case 2: s.append("[1,2]"); break;
                default: s.append("{\"ok\":true}"); break;
            }
        }
        return s.append(']').toString();
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

    private static void verifyEquivalent(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Raw graph differs: " + actual);
        }
    }

    // Main comparison: always keep native / compiled / runtime together.
    @Benchmark
    public Object mapNativeString() {
        try (JSONReader reader = JSONReader.of(json, nativeContext)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object mapCompiledString() throws IOException {
        return compiled.readMap(json);
    }

    @Benchmark
    public Object mapRuntimeString() {
        return binder.readNode(json, Map.class);
    }

    @Benchmark
    public Object mapNativeUtf8() {
        try (JSONReader reader = JSONReader.of(utf8, nativeContext)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object mapCompiledUtf8() throws IOException {
        return compiled.readMapUtf8(utf8);
    }

    @Benchmark
    public Object mapRuntimeUtf8() {
        return binder.readNode(utf8, Map.class);
    }

    @Benchmark
    public Object listNativeString() {
        try (JSONReader reader = JSONReader.of(arrayJson, nativeContext)) {
            return reader.read(List.class);
        }
    }

    @Benchmark
    public Object listCompiledString() throws IOException {
        return compiled.readList(arrayJson);
    }

    @Benchmark
    public Object listRuntimeString() {
        return binder.readNode(arrayJson, List.class);
    }

    @Benchmark
    public Object listNativeUtf8() {
        try (JSONReader reader = JSONReader.of(arrayUtf8, nativeContext)) {
            return reader.read(List.class);
        }
    }

    @Benchmark
    public Object listCompiledUtf8() throws IOException {
        return compiled.readListUtf8(arrayUtf8);
    }

    @Benchmark
    public Object listRuntimeUtf8() {
        return binder.readNode(arrayUtf8, List.class);
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface RawCompiledBinder {
        @ReadFrom Map<String, Object> readMap(String input) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] input) throws IOException;
        @ReadFrom List<Object> readList(String input) throws IOException;
        @ReadFrom List<Object> readListUtf8(byte[] input) throws IOException;
    }
}
