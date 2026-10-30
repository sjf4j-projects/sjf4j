package org.sjf4j.testbench.binding.compiled;

import com.google.gson.Gson;
import jakarta.json.Json;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Size-based Raw Map/List JMH for Gson, JSON-P and SJF4J Simple.
 *
 * All compiled and runtime methods return the same native-Java Map/List graph.
 * Gson native also returns a Java collection; JSON-P native_tree returns a
 * JsonValue tree, so it is not a directly comparable Map/List baseline.
 * Simple has no independent native implementation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 15, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
public class OtherRawSizeBenchmark {
    @Param({"0", "1", "2", "3", "4", "8", "32"})
    public int count;

    private static final Gson GSON_NATIVE = new Gson();
    private static final GsonBinder GSON_RUNTIME = new GsonBinder();
    private static final JsonpBinder JSONP_RUNTIME = new JsonpBinder();
    private static final SimpleJsonBinder SIMPLE_RUNTIME = new SimpleJsonBinder();
    private static final GsonCompiled GSON_COMPILED = CompiledInstances.create(GsonCompiled.class);
    private static final JsonpCompiled JSONP_COMPILED = CompiledInstances.create(JsonpCompiled.class);
    private static final SimpleCompiled SIMPLE_COMPILED = CompiledInstances.create(SimpleCompiled.class);

    private String objectJson;
    private String arrayJson;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        StringBuilder obj = new StringBuilder("{");
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i != 0) { obj.append(','); arr.append(','); }
            String value = i % 4 == 0 ? "\"value" + i + "\""
                    : i % 4 == 1 ? "true"
                    : i % 4 == 2 ? "[false,null]" : "{\"nested\":\"v\"}";
            obj.append('"').append('k').append(i).append("\":").append(value);
            arr.append(value);
        }
        objectJson = obj.append('}').toString();
        arrayJson = arr.append(']').toString();

        Map<String, Object> map = GSON_COMPILED.map(objectJson);
        List<Object> list = GSON_COMPILED.list(arrayJson);
        assertSame(map, GSON_NATIVE.fromJson(objectJson, Map.class));
        assertSame(map, GSON_RUNTIME.readNode(objectJson, Map.class));
        assertSame(map, JSONP_COMPILED.map(objectJson));
        assertSame(map, JSONP_RUNTIME.readNode(objectJson, Map.class));
        assertSame(map, SIMPLE_COMPILED.map(objectJson));
        assertSame(map, SIMPLE_RUNTIME.readNode(objectJson, Map.class));
        assertSame(list, GSON_NATIVE.fromJson(arrayJson, List.class));
        assertSame(list, GSON_RUNTIME.readNode(arrayJson, List.class));
        assertSame(list, JSONP_COMPILED.list(arrayJson));
        assertSame(list, JSONP_RUNTIME.readNode(arrayJson, List.class));
        assertSame(list, SIMPLE_COMPILED.list(arrayJson));
        assertSame(list, SIMPLE_RUNTIME.readNode(arrayJson, List.class));
    }

    private static void assertSame(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Raw value graph differs: " + actual);
        }
    }

    @Benchmark public Object gson_map_native() { return GSON_NATIVE.fromJson(objectJson, Map.class); }
    @Benchmark public Object gson_map_compiled() throws IOException { return GSON_COMPILED.map(objectJson); }
    @Benchmark public Object gson_map_runtime() { return GSON_RUNTIME.readNode(objectJson, Map.class); }
    @Benchmark public Object gson_list_native() { return GSON_NATIVE.fromJson(arrayJson, List.class); }
    @Benchmark public Object gson_list_compiled() throws IOException { return GSON_COMPILED.list(arrayJson); }
    @Benchmark public Object gson_list_runtime() { return GSON_RUNTIME.readNode(arrayJson, List.class); }

    @Benchmark public Object jsonp_map_compiled() throws IOException { return JSONP_COMPILED.map(objectJson); }
    @Benchmark public Object jsonp_map_runtime() { return JSONP_RUNTIME.readNode(objectJson, Map.class); }
    @Benchmark public Object jsonp_list_compiled() throws IOException { return JSONP_COMPILED.list(arrayJson); }
    @Benchmark public Object jsonp_list_runtime() { return JSONP_RUNTIME.readNode(arrayJson, List.class); }
    @Benchmark public Object jsonp_map_native_tree() {
        try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(objectJson))) {
            return reader.readObject();
        }
    }
    @Benchmark public Object jsonp_list_native_tree() {
        try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(arrayJson))) {
            return reader.readArray();
        }
    }

    @Benchmark public Object simple_map_compiled() throws IOException { return SIMPLE_COMPILED.map(objectJson); }
    @Benchmark public Object simple_map_runtime() { return SIMPLE_RUNTIME.readNode(objectJson, Map.class); }
    @Benchmark public Object simple_list_compiled() throws IOException { return SIMPLE_COMPILED.list(arrayJson); }
    @Benchmark public Object simple_list_runtime() { return SIMPLE_RUNTIME.readNode(arrayJson, List.class); }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiled {
        @ReadFrom Map<String, Object> map(String input) throws IOException;
        @ReadFrom List<Object> list(String input) throws IOException;
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCompiled {
        @ReadFrom Map<String, Object> map(String input) throws IOException;
        @ReadFrom List<Object> list(String input) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCompiled {
        @ReadFrom Map<String, Object> map(String input) throws IOException;
        @ReadFrom List<Object> list(String input) throws IOException;
    }
}
