package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Controlled size study for Jackson raw container specialization.
 * Each native / compiled / runtime trio uses the same JSON shape and input
 * representation, including parser construction and disposal. No conversion
 * of returned containers is timed. Run with -prof gc to inspect B/op.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 15, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class JacksonRawSizeBenchmark {
    @Param({"0", "1", "2", "5", "6", "32"})
    public int count;

    private static final ObjectMapper JACKSON2_NATIVE = new ObjectMapper();
    private static final JsonMapper JACKSON3_NATIVE = new JsonMapper();
    private static final Jackson2Binder JACKSON2_RUNTIME = new Jackson2Binder();
    private static final Jackson3Binder JACKSON3_RUNTIME = new Jackson3Binder();
    private static final Jackson2Compiled JACKSON2_COMPILED =
            CompiledInstances.create(Jackson2Compiled.class);
    private static final Jackson3Compiled JACKSON3_COMPILED =
            CompiledInstances.create(Jackson3Compiled.class);

    private String objectJson;
    private byte[] objectUtf8;
    private String arrayJson;
    private byte[] arrayUtf8;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        StringBuilder object = new StringBuilder("{");
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i != 0) {
                object.append(',');
                array.append(',');
            }
            String value = i % 4 == 0 ? Integer.toString(i)
                    : i % 4 == 1 ? "\"v" + i + "\""
                    : i % 4 == 2 ? "[1,2,null]" : "{\"ok\":true}";
            object.append('"').append('k').append(i).append("\":").append(value);
            array.append(value);
        }
        objectJson = object.append('}').toString();
        arrayJson = array.append(']').toString();
        objectUtf8 = objectJson.getBytes(StandardCharsets.UTF_8);
        arrayUtf8 = arrayJson.getBytes(StandardCharsets.UTF_8);

        Object map = JACKSON2_NATIVE.readValue(objectJson, Map.class);
        Object list = JACKSON2_NATIVE.readValue(arrayJson, List.class);
        verify(map, JACKSON2_COMPILED.map(objectJson));
        verify(map, JACKSON2_COMPILED.map(objectUtf8));
        verify(map, JACKSON2_RUNTIME.readNode(objectJson, Map.class));
        verify(map, JACKSON2_RUNTIME.readNode(objectUtf8, Map.class));
        verify(map, JACKSON3_NATIVE.readValue(objectJson, Map.class));
        verify(map, JACKSON3_COMPILED.map(objectJson));
        verify(map, JACKSON3_COMPILED.map(objectUtf8));
        verify(map, JACKSON3_RUNTIME.readNode(objectJson, Map.class));
        verify(map, JACKSON3_RUNTIME.readNode(objectUtf8, Map.class));
        verify(list, JACKSON2_COMPILED.list(arrayJson));
        verify(list, JACKSON2_COMPILED.list(arrayUtf8));
        verify(list, JACKSON2_RUNTIME.readNode(arrayJson, List.class));
        verify(list, JACKSON2_RUNTIME.readNode(arrayUtf8, List.class));
        verify(list, JACKSON3_NATIVE.readValue(arrayJson, List.class));
        verify(list, JACKSON3_COMPILED.list(arrayJson));
        verify(list, JACKSON3_COMPILED.list(arrayUtf8));
        verify(list, JACKSON3_RUNTIME.readNode(arrayJson, List.class));
        verify(list, JACKSON3_RUNTIME.readNode(arrayUtf8, List.class));
    }

    private static void verify(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Raw container graph differs: " + actual);
        }
    }

    @Benchmark public Object jackson2_map_string_native() throws Exception { return JACKSON2_NATIVE.readValue(objectJson, Map.class); }
    @Benchmark public Object jackson2_map_string_compiled() throws IOException { return JACKSON2_COMPILED.map(objectJson); }
    @Benchmark public Object jackson2_map_string_runtime() { return JACKSON2_RUNTIME.readNode(objectJson, Map.class); }
    @Benchmark public Object jackson2_map_utf8_native() throws Exception { return JACKSON2_NATIVE.readValue(objectUtf8, Map.class); }
    @Benchmark public Object jackson2_map_utf8_compiled() throws IOException { return JACKSON2_COMPILED.map(objectUtf8); }
    @Benchmark public Object jackson2_map_utf8_runtime() { return JACKSON2_RUNTIME.readNode(objectUtf8, Map.class); }

    @Benchmark public Object jackson3_map_string_native() throws Exception { return JACKSON3_NATIVE.readValue(objectJson, Map.class); }
    @Benchmark public Object jackson3_map_string_compiled() throws IOException { return JACKSON3_COMPILED.map(objectJson); }
    @Benchmark public Object jackson3_map_string_runtime() { return JACKSON3_RUNTIME.readNode(objectJson, Map.class); }
    @Benchmark public Object jackson3_map_utf8_native() throws Exception { return JACKSON3_NATIVE.readValue(objectUtf8, Map.class); }
    @Benchmark public Object jackson3_map_utf8_compiled() throws IOException { return JACKSON3_COMPILED.map(objectUtf8); }
    @Benchmark public Object jackson3_map_utf8_runtime() { return JACKSON3_RUNTIME.readNode(objectUtf8, Map.class); }

    @Benchmark public Object jackson2_list_string_native() throws Exception { return JACKSON2_NATIVE.readValue(arrayJson, List.class); }
    @Benchmark public Object jackson2_list_string_compiled() throws IOException { return JACKSON2_COMPILED.list(arrayJson); }
    @Benchmark public Object jackson2_list_string_runtime() { return JACKSON2_RUNTIME.readNode(arrayJson, List.class); }
    @Benchmark public Object jackson2_list_utf8_native() throws Exception { return JACKSON2_NATIVE.readValue(arrayUtf8, List.class); }
    @Benchmark public Object jackson2_list_utf8_compiled() throws IOException { return JACKSON2_COMPILED.list(arrayUtf8); }
    @Benchmark public Object jackson2_list_utf8_runtime() { return JACKSON2_RUNTIME.readNode(arrayUtf8, List.class); }

    @Benchmark public Object jackson3_list_string_native() throws Exception { return JACKSON3_NATIVE.readValue(arrayJson, List.class); }
    @Benchmark public Object jackson3_list_string_compiled() throws IOException { return JACKSON3_COMPILED.list(arrayJson); }
    @Benchmark public Object jackson3_list_string_runtime() { return JACKSON3_RUNTIME.readNode(arrayJson, List.class); }
    @Benchmark public Object jackson3_list_utf8_native() throws Exception { return JACKSON3_NATIVE.readValue(arrayUtf8, List.class); }
    @Benchmark public Object jackson3_list_utf8_compiled() throws IOException { return JACKSON3_COMPILED.list(arrayUtf8); }
    @Benchmark public Object jackson3_list_utf8_runtime() { return JACKSON3_RUNTIME.readNode(arrayUtf8, List.class); }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Compiled {
        @ReadFrom Map<String, Object> map(String json) throws IOException;
        @ReadFrom Map<String, Object> map(byte[] json) throws IOException;
        @ReadFrom List<Object> list(String json) throws IOException;
        @ReadFrom List<Object> list(byte[] json) throws IOException;
    }
    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Compiled {
        @ReadFrom Map<String, Object> map(String json) throws IOException;
        @ReadFrom Map<String, Object> map(byte[] json) throws IOException;
        @ReadFrom List<Object> list(String json) throws IOException;
        @ReadFrom List<Object> list(byte[] json) throws IOException;
    }
}
