package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.testbench.model.User;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

/**
 * CompiledBinder baseline. Each timed operation includes input/parser creation,
 * full binding, and owned-reader closing. No parsing or conversion in JMH setup
 * is included in the measurements.
 *
 * native: backend-native Map/List binding for the same logical target type.
 * Fastjson2 uses JSONReader.read(Map.class)/read(List.class).
 * native_reader: Gson has no byte[] decoding API; it receives a UTF-8 Reader.
 * native_tree: JSON-P returns JsonValue, NOT a Java Map or POJO; compare separately.
 * No Simple native baseline exists.
 * Fastjson2 native Map results may use JSONObject/JSONArray; compare JSON values,
 * not concrete container classes, when evaluating backend performance.
 *
 * Output: microseconds per operation. Compare identical backend/shape/input.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CompiledMapListBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CompiledMapListBenchmark.class.getName()});
//        Main.main(new String[]{CompiledMapListBenchmark.class.getName() + ".*map"});
    }

    // Use the shared User JSON fixture for independent Map/List benchmarks.
    private static final String JSON_DATA = User.JSON_DATA;
    public static final byte[] JSON_UTF8 = JSON_DATA.getBytes(StandardCharsets.UTF_8);
    public static final String JSON_LIST_DATA =
            "[" + JSON_DATA + ",{\"nested\":[1,true,null]},\"tail\"]";
    public static final byte[] JSON_LIST_UTF8 = JSON_LIST_DATA.getBytes(StandardCharsets.UTF_8);

    private static final ObjectMapper JACKSON2_NATIVE = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final JsonMapper JACKSON3_NATIVE = new JsonMapper();
    private static final Gson GSON_NATIVE = new GsonBuilder().create();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT =
            JSONFactory.createReadContext(new ObjectReaderProvider(),
                    JSONReader.Feature.UseDoubleForDecimals);

    private static final Jackson2Binder JACKSON2_RUNTIME = new Jackson2Binder();
    private static final Jackson3Binder JACKSON3_RUNTIME = new Jackson3Binder();
    private static final GsonBinder GSON_RUNTIME = new GsonBinder();
    private static final Fastjson2Binder FASTJSON2_RUNTIME = new Fastjson2Binder();
    private static final JsonpBinder JSONP_RUNTIME = new JsonpBinder();
    private static final SimpleJsonBinder SIMPLE_RUNTIME = new SimpleJsonBinder();

    private static final Jackson2CompiledBinder JACKSON2_COMPILED =
            CompiledInstances.create(Jackson2CompiledBinder.class);
    private static final Jackson3CompiledBinder JACKSON3_COMPILED =
            CompiledInstances.create(Jackson3CompiledBinder.class);
    private static final GsonCompiledBinder GSON_COMPILED =
            CompiledInstances.create(GsonCompiledBinder.class);
    private static final Fastjson2CompiledBinder FASTJSON2_COMPILED =
            CompiledInstances.create(Fastjson2CompiledBinder.class);
    private static final JsonpCompiledBinder JSONP_COMPILED =
            CompiledInstances.create(JsonpCompiledBinder.class);
    private static final SimpleCompiledBinder SIMPLE_COMPILED =
            CompiledInstances.create(SimpleCompiledBinder.class);

    @Setup(Level.Trial)
    public void verify() throws Exception {
        Object jackson2Map = JACKSON2_COMPILED.readMap(JSON_DATA);
        Object jackson3Map = JACKSON3_COMPILED.readMap(JSON_DATA);
        Object map = FASTJSON2_COMPILED.readMap(JSON_DATA);
        Object list = FASTJSON2_COMPILED.readList(JSON_LIST_DATA);
        checkMap("jackson2 compiled JSON_DATA", JACKSON2_COMPILED.readMap(JSON_DATA));
        checkMap("jackson2 runtime JSON_DATA", JACKSON2_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("jackson2 compiled JSON_UTF8", JACKSON2_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("jackson2 runtime JSON_UTF8", JACKSON2_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkEquivalentRawGraph("jackson2 native String", jackson2Map, jackson2_map_string_native());
        checkEquivalentRawGraph("jackson2 raw String", jackson2Map, jackson2_map_string_raw());
        checkEquivalentRawGraph("jackson2 runtime String", jackson2Map, jackson2_map_string_runtime());
        checkEquivalentRawGraph("jackson2 native UTF-8", jackson2Map, jackson2_map_utf8_native());
        checkEquivalentRawGraph("jackson2 compiled UTF-8", jackson2Map, jackson2_map_utf8_compiled());
        checkEquivalentRawGraph("jackson2 raw UTF-8", jackson2Map, jackson2_map_utf8_raw());
        checkEquivalentRawGraph("jackson2 runtime UTF-8", jackson2Map, jackson2_map_utf8_runtime());
        checkList("jackson2 compiled list", JACKSON2_COMPILED.readList(JSON_LIST_DATA));
        checkList("jackson2 runtime list", JACKSON2_RUNTIME.readNode(JSON_LIST_DATA, java.util.List.class));
        checkList("jackson2 compiled list utf8", JACKSON2_COMPILED.readListUtf8(JSON_LIST_UTF8));
        checkMap("jackson3 compiled JSON_DATA", JACKSON3_COMPILED.readMap(JSON_DATA));
        checkMap("jackson3 runtime JSON_DATA", JACKSON3_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("jackson3 compiled JSON_UTF8", JACKSON3_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("jackson3 runtime JSON_UTF8", JACKSON3_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkEquivalentRawGraph("jackson3 native String", jackson3Map, jackson3_map_string_native());
        checkEquivalentRawGraph("jackson3 raw String", jackson3Map, jackson3_map_string_raw());
        checkEquivalentRawGraph("jackson3 runtime String", jackson3Map, jackson3_map_string_runtime());
        checkEquivalentRawGraph("jackson3 native UTF-8", jackson3Map, jackson3_map_utf8_native());
        checkEquivalentRawGraph("jackson3 compiled UTF-8", jackson3Map, jackson3_map_utf8_compiled());
        checkEquivalentRawGraph("jackson3 raw UTF-8", jackson3Map, jackson3_map_utf8_raw());
        checkEquivalentRawGraph("jackson3 runtime UTF-8", jackson3Map, jackson3_map_utf8_runtime());
        checkMap("gson compiled JSON_DATA", GSON_COMPILED.readMap(JSON_DATA));
        checkMap("gson runtime JSON_DATA", GSON_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("gson compiled JSON_UTF8", GSON_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("gson runtime JSON_UTF8", GSON_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkMap("fastjson2 compiled JSON_DATA", FASTJSON2_COMPILED.readMap(JSON_DATA));
        checkMap("fastjson2 runtime JSON_DATA", FASTJSON2_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("fastjson2 compiled JSON_UTF8", FASTJSON2_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("fastjson2 runtime JSON_UTF8", FASTJSON2_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkEquivalentRawGraph("fastjson2 map String native", map, fastjson2_map_string_native());
        checkEquivalentRawGraph("fastjson2 map UTF-8 native", map, fastjson2_map_utf8_native());
        checkEquivalentRawGraph("fastjson2 map String runtime", map,
                FASTJSON2_RUNTIME.readNode(JSON_DATA, Map.class));
        checkEquivalentRawGraph("fastjson2 map UTF-8 compiled", map,
                FASTJSON2_COMPILED.readMapUtf8(JSON_UTF8));
        checkEquivalentRawGraph("fastjson2 map UTF-8 runtime", map,
                FASTJSON2_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkList("fastjson2 compiled list", list);
        checkList("fastjson2 runtime list", FASTJSON2_RUNTIME.readNode(JSON_LIST_DATA, List.class));
        checkList("fastjson2 compiled list utf8", FASTJSON2_COMPILED.readListUtf8(JSON_LIST_UTF8));
        checkEquivalentRawGraph("fastjson2 list String native", list, fastjson2_list_string_native());
        checkEquivalentRawGraph("fastjson2 list UTF-8 native", list, fastjson2_list_utf8_native());
        checkEquivalentRawGraph("fastjson2 list UTF-8 runtime", list,
                FASTJSON2_RUNTIME.readNode(JSON_LIST_UTF8, List.class));
        checkMap("jsonp compiled JSON_DATA", JSONP_COMPILED.readMap(JSON_DATA));
        checkMap("jsonp runtime JSON_DATA", JSONP_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("jsonp compiled JSON_UTF8", JSONP_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("jsonp runtime JSON_UTF8", JSONP_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkMap("simple compiled JSON_DATA", SIMPLE_COMPILED.readMap(JSON_DATA));
        checkMap("simple runtime JSON_DATA", SIMPLE_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("simple compiled JSON_UTF8", SIMPLE_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("simple runtime JSON_UTF8", SIMPLE_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkMap("jackson2 native string", jackson2_map_string_native());
        checkMap("jackson2 native utf8", jackson2_map_utf8_native());
        checkMap("jackson3 native string", jackson3_map_string_native());
        checkMap("jackson3 native utf8", jackson3_map_utf8_native());
        checkMap("gson native string", gson_map_string_native());
        checkMap("gson native utf8", gson_map_utf8_native_reader());
        checkMap("fastjson2 native string", fastjson2_map_string_native());
        checkMap("fastjson2 native utf8", fastjson2_map_utf8_native());
    }

    private static void checkMap(String label, Object result) {
        if (!(result instanceof Map)
                || !((Map<?, ?>) result).containsKey("friends")
                || !((Map<?, ?>) result).containsKey("address")) {
            throw new IllegalStateException("Map result differs: " + label);
        }
    }

    private static void checkList(String label, Object result) {
        if (!(result instanceof java.util.ArrayList)
                || ((java.util.List<?>) result).size() != 3
                || !(((java.util.List<?>) result).get(0) instanceof java.util.LinkedHashMap)
                || !(((java.util.List<?>) result).get(1) instanceof java.util.Map)) {
            throw new IllegalStateException("List result differs: " + label);
        }
    }

    // Compare JSON values, not Java implementation classes. Numeric equality
    // is still checked (e.g. Double vs BigDecimal is NOT silently ignored).
    private static void checkEquivalentRawGraph(String label, Object expected, Object actual) {
        if (expected == null || actual == null) {
            if (expected != actual) throw new IllegalStateException("Raw null differs: " + label);
            return;
        }
        if ((expected instanceof Map) && (actual instanceof Map)) {
            Map<?, ?> a = (Map<?, ?>) expected, b = (Map<?, ?>) actual;
            if (a.size() != b.size() || !a.keySet().equals(b.keySet())) {
                throw new IllegalStateException("Raw object keys differ: " + label);
            }
            for (Map.Entry<?, ?> member : a.entrySet()) {
                checkEquivalentRawGraph(label + "." + member.getKey(),
                        member.getValue(), b.get(member.getKey()));
            }
            return;
        }
        if ((expected instanceof List) && (actual instanceof List)) {
            List<?> a = (List<?>) expected, b = (List<?>) actual;
            if (a.size() != b.size()) throw new IllegalStateException("Raw array size differs: " + label);
            for (int i = 0; i < a.size(); i++) {
                checkEquivalentRawGraph(label + "[" + i + "]", a.get(i), b.get(i));
            }
            return;
        }
        if (expected instanceof Number && actual instanceof Number) {
            if (new java.math.BigDecimal(expected.toString())
                    .compareTo(new java.math.BigDecimal(actual.toString())) != 0) {
                throw new IllegalStateException("Raw number differs: " + label);
            }
            return;
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Raw scalar differs: " + label + " expected " + expected
                    + ", actual " + actual);
        }
    }

    // ----- JACKSON2 -----
    // Exercise the same compiled name-match path with the opposite field
    // order; the existing JSON_DATA benchmark covers ordered fields.

    @Benchmark
    public Object jackson2_map_string_compiled() throws IOException {
        return JACKSON2_COMPILED.readMap(JSON_DATA);
    }

    @Benchmark
    public Object jackson2_map_string_runtime() {
        return JACKSON2_RUNTIME.readNode(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object jackson2_map_string_native() throws Exception {
        return JACKSON2_NATIVE.readValue(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object jackson2_map_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.readMapUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object jackson2_map_utf8_runtime() {
        return JACKSON2_RUNTIME.readNode(JSON_UTF8, Map.class);
    }

    @Benchmark
    public Object jackson2_map_utf8_native() throws Exception {
        return JACKSON2_NATIVE.readValue(JSON_UTF8, Map.class);
    }

    // Direct raw-object parsing baseline: includes parser creation, lifecycle
    // and backend-native recursive OBNT materialization, like other methods.
    @Benchmark
    public Object jackson2_map_string_raw() throws IOException {
        try (StreamingParser parser = JACKSON2_RUNTIME.createParser(JSON_DATA)) {
            parser.start();
            Map<String, Object> value = parser.readRawObject();
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Object jackson2_map_utf8_raw() throws IOException {
        try (StreamingParser parser = JACKSON2_RUNTIME.createParser(JSON_UTF8)) {
            parser.start();
            Map<String, Object> value = parser.readRawObject();
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Object jackson2_list_string_compiled() throws IOException {
        return JACKSON2_COMPILED.readList(JSON_LIST_DATA);
    }

    @Benchmark
    public Object jackson2_list_string_runtime() {
        return JACKSON2_RUNTIME.readNode(JSON_LIST_DATA, java.util.List.class);
    }

    @Benchmark
    public Object jackson2_list_string_raw() throws IOException {
        try (StreamingParser parser = JACKSON2_RUNTIME.createParser(JSON_LIST_DATA)) {
            parser.start();
            java.util.List<Object> value = parser.readRawArray();
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Object jackson2_list_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.readListUtf8(JSON_LIST_UTF8);
    }

    @Benchmark
    public Object jackson2_list_utf8_runtime() {
        return JACKSON2_RUNTIME.readNode(JSON_LIST_UTF8, java.util.List.class);
    }

    @Benchmark
    public Object jackson2_list_utf8_raw() throws IOException {
        try (StreamingParser parser = JACKSON2_RUNTIME.createParser(JSON_LIST_UTF8)) {
            parser.start();
            java.util.List<Object> value = parser.readRawArray();
            parser.end();
            return value;
        }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinder {
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
        @ReadFrom java.util.List<Object> readList(String json) throws IOException;
        @ReadFrom java.util.List<Object> readListUtf8(byte[] json) throws IOException;
    }

    // ----- JACKSON3 -----

    @Benchmark
    public Object jackson3_map_string_compiled() throws IOException {
        return JACKSON3_COMPILED.readMap(JSON_DATA);
    }

    @Benchmark
    public Object jackson3_map_string_runtime() {
        return JACKSON3_RUNTIME.readNode(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object jackson3_map_string_native() throws Exception {
        return JACKSON3_NATIVE.readValue(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object jackson3_map_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.readMapUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object jackson3_map_utf8_runtime() {
        return JACKSON3_RUNTIME.readNode(JSON_UTF8, Map.class);
    }

    @Benchmark
    public Object jackson3_map_utf8_native() throws Exception {
        return JACKSON3_NATIVE.readValue(JSON_UTF8, Map.class);
    }

    @Benchmark
    public Object jackson3_map_string_raw() throws IOException {
        try (StreamingParser parser = JACKSON3_RUNTIME.createParser(JSON_DATA)) {
            parser.start();
            Map<String, Object> value = parser.readRawObject();
            parser.end();
            return value;
        }
    }

    @Benchmark
    public Object jackson3_map_utf8_raw() throws IOException {
        try (StreamingParser parser = JACKSON3_RUNTIME.createParser(JSON_UTF8)) {
            parser.start();
            Map<String, Object> value = parser.readRawObject();
            parser.end();
            return value;
        }
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3CompiledBinder {
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- GSON -----

    @Benchmark
    public Object gson_map_string_compiled() throws IOException {
        return GSON_COMPILED.readMap(JSON_DATA);
    }

    @Benchmark
    public Object gson_map_string_runtime() {
        return GSON_RUNTIME.readNode(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object gson_map_string_native() throws Exception {
        return GSON_NATIVE.fromJson(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object gson_map_utf8_compiled() throws IOException {
        return GSON_COMPILED.readMapUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object gson_map_utf8_runtime() {
        return GSON_RUNTIME.readNode(JSON_UTF8, Map.class);
    }

    @Benchmark
    public Object gson_map_utf8_native_reader() throws Exception {
        return GSON_NATIVE.fromJson(new InputStreamReader(
                new ByteArrayInputStream(JSON_UTF8), StandardCharsets.UTF_8), Map.class);
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiledBinder {
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- FASTJSON2 -----

    // Compare native typed Map/List binding, compiled, and runtime on the
    // same payload. SJF4J owns its raw recursion and JDK container types.
    @Benchmark
    public Object fastjson2_map_string_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object fastjson2_map_string_compiled() throws IOException {
        return FASTJSON2_COMPILED.readMap(JSON_DATA);
    }

    @Benchmark
    public Object fastjson2_map_string_runtime() {
        return FASTJSON2_RUNTIME.readNode(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object fastjson2_map_utf8_native() {
        try (JSONReader reader = JSONReader.of(JSON_UTF8, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object fastjson2_map_utf8_compiled() throws IOException {
        return FASTJSON2_COMPILED.readMapUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object fastjson2_map_utf8_runtime() {
        return FASTJSON2_RUNTIME.readNode(JSON_UTF8, Map.class);
    }

    @Benchmark
    public Object fastjson2_list_string_native() {
        try (JSONReader reader = JSONReader.of(JSON_LIST_DATA, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(List.class);
        }
    }

    @Benchmark
    public Object fastjson2_list_string_compiled() throws IOException {
        return FASTJSON2_COMPILED.readList(JSON_LIST_DATA);
    }

    @Benchmark
    public Object fastjson2_list_string_runtime() {
        return FASTJSON2_RUNTIME.readNode(JSON_LIST_DATA, List.class);
    }

    @Benchmark
    public Object fastjson2_list_utf8_native() {
        try (JSONReader reader = JSONReader.of(JSON_LIST_UTF8, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(List.class);
        }
    }

    @Benchmark
    public Object fastjson2_list_utf8_compiled() throws IOException {
        return FASTJSON2_COMPILED.readListUtf8(JSON_LIST_UTF8);
    }

    @Benchmark
    public Object fastjson2_list_utf8_runtime() {
        return FASTJSON2_RUNTIME.readNode(JSON_LIST_UTF8, List.class);
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2CompiledBinder {
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
        @ReadFrom java.util.List<Object> readList(String json) throws IOException;
        @ReadFrom java.util.List<Object> readListUtf8(byte[] json) throws IOException;
    }

    // ----- JSONP -----

    @Benchmark
    public Object jsonp_map_string_compiled() throws IOException {
        return JSONP_COMPILED.readMap(JSON_DATA);
    }

    @Benchmark
    public Object jsonp_map_string_runtime() {
        return JSONP_RUNTIME.readNode(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object jsonp_map_utf8_compiled() throws IOException {
        return JSONP_COMPILED.readMapUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object jsonp_map_utf8_runtime() {
        return JSONP_RUNTIME.readNode(JSON_UTF8, Map.class);
    }

    // A separate JSON-P tree reference only; NOT the same return type as Map.
    @Benchmark
    public Object jsonp_tree_string_native() {
        try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(JSON_DATA))) {
            return reader.read();
        }
    }

    @Benchmark
    public Object jsonp_tree_utf8_native() {
        try (jakarta.json.JsonReader reader = Json.createReader(new ByteArrayInputStream(JSON_UTF8))) {
            return reader.read();
        }
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCompiledBinder {
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- SIMPLE -----

    @Benchmark
    public Object simple_map_string_compiled() throws IOException {
        return SIMPLE_COMPILED.readMap(JSON_DATA);
    }

    @Benchmark
    public Object simple_map_string_runtime() {
        return SIMPLE_RUNTIME.readNode(JSON_DATA, Map.class);
    }

    @Benchmark
    public Object simple_map_utf8_compiled() throws IOException {
        return SIMPLE_COMPILED.readMapUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object simple_map_utf8_runtime() {
        return SIMPLE_RUNTIME.readNode(JSON_UTF8, Map.class);
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCompiledBinder {
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

}
