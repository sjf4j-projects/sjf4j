package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CompiledBinder baseline. Each timed operation includes input/parser creation,
 * full binding, and owned-reader closing. No parsing or conversion in JMH setup
 * is included in the measurements.
 *
 * native: framework public data-binding API for the same Java target type.
 * native_reader: Gson has no byte[] decoding API; it receives a UTF-8 Reader.
 * native_tree: JSON-P returns JsonValue, NOT a Java Map or POJO; compare separately.
 * No Simple native baseline exists.
 *
 * Output: microseconds per operation. Compare identical backend/shape/input.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CompiledReadBenchmark {

    public static void main(String[] args) throws Exception {
//        Main.main(new String[]{CompiledReadBenchmark.class.getName()});
        Main.main(new String[]{CompiledReadBenchmark.class.getName() + ".fastjson2"});
    }


    // Reuse the User fixture in the former compiled benchmark. Includes nested
    // POJOs, arrays/lists, primitive fields, nulls and escaping.
    public static final String JSON_DATA = "{\n" +
            "  \"id\": 839201, \"createdAt\": 1700000000123, \"updatedAt\": 1701234567890,\n" +
            "  \"reputation\": 9876543210, \"loginCount\": 421, \"age\": 34,\n" +
            "  \"active\": true, \"verified\": true, \"admin\": false, \"suspended\": false,\n" +
            "  \"score\": 98.75, \"latitude\": 37.7749, \"longitude\": -122.4194,\n" +
            "  \"username\": \"alice.builder\", \"email\": null,\n" +
            "  \"displayName\": \"Alice \\\"the Builder\\\" n Line\", \"passwordHash\": \"$2a$12$abcdef\",\n" +
            "  \"bio\": \"Builds fast JSON with control chars\", \"website\": \"https://example.test/a?b=1\",\n" +
            "  \"department\": \"Platform Engineering\",\n" +
            "  \"address\": {\"street\": \"1 Main St\", \"city\": \"San Francisco\", \"state\": \"CA\", \"zip\": \"94105\", \"country\": \"US\"},\n" +
            "  \"tags\": [\"tag-0-value\", null],\n" +
            "  \"friends\": [\n" +
            "    {\"id\": 1000, \"name\": \"BillBackslash\", \"since\": 1600000000000, \"close\": true},\n" +
            "    null,\n" +
            "    {\"id\": 1001, \"name\": \"Cindy Line\", \"since\": 1600000000001, \"close\": false}\n" +
            "  ]\n" +
            "}\n";
    public static final byte[] JSON_UTF8 = JSON_DATA.getBytes(StandardCharsets.UTF_8);

    private static final ObjectMapper JACKSON2_NATIVE = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final ObjectMapper JACKSON2_BLACKBIRD = new ObjectMapper()
            .registerModule(new BlackbirdModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final JsonMapper JACKSON3_NATIVE = new JsonMapper();
    private static final JsonMapper JACKSON3_BLACKBIRD = JsonMapper.builder()
            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build();
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
            CompiledInstances.of(Jackson2CompiledBinder.class);
    private static final Jackson3CompiledBinder JACKSON3_COMPILED =
            CompiledInstances.of(Jackson3CompiledBinder.class);
    private static final GsonCompiledBinder GSON_COMPILED =
            CompiledInstances.of(GsonCompiledBinder.class);
    private static final Fastjson2CompiledBinder FASTJSON2_COMPILED =
            CompiledInstances.of(Fastjson2CompiledBinder.class);
    private static final JsonpCompiledBinder JSONP_COMPILED =
            CompiledInstances.of(JsonpCompiledBinder.class);
    private static final SimpleCompiledBinder SIMPLE_COMPILED =
            CompiledInstances.of(SimpleCompiledBinder.class);

    // Fail in trial setup if a compiled or runtime result differs from the
    // reference POJO. This catches accidentally unequal benchmark fixtures.
    @Setup(Level.Trial)
    public void verify() throws Exception {
        User expected = JACKSON2_NATIVE.readValue(JSON_DATA, User.class);
        checkPojo("jackson2 compiled JSON_DATA", expected, JACKSON2_COMPILED.readPojo(JSON_DATA));
        checkPojo("jackson2 runtime JSON_DATA", expected, JACKSON2_RUNTIME.readNode(JSON_DATA, User.class));
        checkPojo("jackson2 compiled JSON_UTF8", expected, JACKSON2_COMPILED.readPojoUtf8(JSON_UTF8));
        checkPojo("jackson2 runtime JSON_UTF8", expected, JACKSON2_RUNTIME.readNode(JSON_UTF8, User.class));
        checkMap("jackson2 compiled JSON_DATA", JACKSON2_COMPILED.readMap(JSON_DATA));
        checkMap("jackson2 runtime JSON_DATA", JACKSON2_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("jackson2 compiled JSON_UTF8", JACKSON2_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("jackson2 runtime JSON_UTF8", JACKSON2_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkPojo("jackson3 compiled JSON_DATA", expected, JACKSON3_COMPILED.readPojo(JSON_DATA));
        checkPojo("jackson3 runtime JSON_DATA", expected, JACKSON3_RUNTIME.readNode(JSON_DATA, User.class));
        checkPojo("jackson3 compiled JSON_UTF8", expected, JACKSON3_COMPILED.readPojoUtf8(JSON_UTF8));
        checkPojo("jackson3 runtime JSON_UTF8", expected, JACKSON3_RUNTIME.readNode(JSON_UTF8, User.class));
        checkMap("jackson3 compiled JSON_DATA", JACKSON3_COMPILED.readMap(JSON_DATA));
        checkMap("jackson3 runtime JSON_DATA", JACKSON3_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("jackson3 compiled JSON_UTF8", JACKSON3_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("jackson3 runtime JSON_UTF8", JACKSON3_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkPojo("gson compiled JSON_DATA", expected, GSON_COMPILED.readPojo(JSON_DATA));
        checkPojo("gson runtime JSON_DATA", expected, GSON_RUNTIME.readNode(JSON_DATA, User.class));
        checkPojo("gson compiled JSON_UTF8", expected, GSON_COMPILED.readPojoUtf8(JSON_UTF8));
        checkPojo("gson runtime JSON_UTF8", expected, GSON_RUNTIME.readNode(JSON_UTF8, User.class));
        checkMap("gson compiled JSON_DATA", GSON_COMPILED.readMap(JSON_DATA));
        checkMap("gson runtime JSON_DATA", GSON_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("gson compiled JSON_UTF8", GSON_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("gson runtime JSON_UTF8", GSON_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkPojo("fastjson2 compiled JSON_DATA", expected, FASTJSON2_COMPILED.readPojo(JSON_DATA));
        checkPojo("fastjson2 runtime JSON_DATA", expected, FASTJSON2_RUNTIME.readNode(JSON_DATA, User.class));
        checkPojo("fastjson2 compiled JSON_UTF8", expected, FASTJSON2_COMPILED.readPojoUtf8(JSON_UTF8));
        checkPojo("fastjson2 runtime JSON_UTF8", expected, FASTJSON2_RUNTIME.readNode(JSON_UTF8, User.class));
        checkMap("fastjson2 compiled JSON_DATA", FASTJSON2_COMPILED.readMap(JSON_DATA));
        checkMap("fastjson2 runtime JSON_DATA", FASTJSON2_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("fastjson2 compiled JSON_UTF8", FASTJSON2_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("fastjson2 runtime JSON_UTF8", FASTJSON2_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkPojo("jsonp compiled JSON_DATA", expected, JSONP_COMPILED.readPojo(JSON_DATA));
        checkPojo("jsonp runtime JSON_DATA", expected, JSONP_RUNTIME.readNode(JSON_DATA, User.class));
        checkPojo("jsonp compiled JSON_UTF8", expected, JSONP_COMPILED.readPojoUtf8(JSON_UTF8));
        checkPojo("jsonp runtime JSON_UTF8", expected, JSONP_RUNTIME.readNode(JSON_UTF8, User.class));
        checkMap("jsonp compiled JSON_DATA", JSONP_COMPILED.readMap(JSON_DATA));
        checkMap("jsonp runtime JSON_DATA", JSONP_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("jsonp compiled JSON_UTF8", JSONP_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("jsonp runtime JSON_UTF8", JSONP_RUNTIME.readNode(JSON_UTF8, Map.class));
        checkPojo("simple compiled JSON_DATA", expected, SIMPLE_COMPILED.readPojo(JSON_DATA));
        checkPojo("simple runtime JSON_DATA", expected, SIMPLE_RUNTIME.readNode(JSON_DATA, User.class));
        checkPojo("simple compiled JSON_UTF8", expected, SIMPLE_COMPILED.readPojoUtf8(JSON_UTF8));
        checkPojo("simple runtime JSON_UTF8", expected, SIMPLE_RUNTIME.readNode(JSON_UTF8, User.class));
        checkMap("simple compiled JSON_DATA", SIMPLE_COMPILED.readMap(JSON_DATA));
        checkMap("simple runtime JSON_DATA", SIMPLE_RUNTIME.readNode(JSON_DATA, Map.class));
        checkMap("simple compiled JSON_UTF8", SIMPLE_COMPILED.readMapUtf8(JSON_UTF8));
        checkMap("simple runtime JSON_UTF8", SIMPLE_RUNTIME.readNode(JSON_UTF8, Map.class));

        checkPojo("jackson2 native string", expected, jackson2_pojo_string_native());
        checkPojo("jackson2 blackbird string", expected, jackson2_pojo_string_blackbird());
        checkPojo("jackson2 native utf8", expected, jackson2_pojo_utf8_native());
        checkPojo("jackson2 blackbird utf8", expected, jackson2_pojo_utf8_blackbird());
        checkMap("jackson2 native string", jackson2_map_string_native());
        checkMap("jackson2 native utf8", jackson2_map_utf8_native());
        checkPojo("jackson3 native string", expected, jackson3_pojo_string_native());
        checkPojo("jackson3 blackbird string", expected, jackson3_pojo_string_blackbird());
        checkPojo("jackson3 native utf8", expected, jackson3_pojo_utf8_native());
        checkPojo("jackson3 blackbird utf8", expected, jackson3_pojo_utf8_blackbird());
        checkMap("jackson3 native string", jackson3_map_string_native());
        checkMap("jackson3 native utf8", jackson3_map_utf8_native());
        checkPojo("gson native string", expected, gson_pojo_string_native());
        checkPojo("gson native utf8", expected, gson_pojo_utf8_native_reader());
        checkMap("gson native string", gson_map_string_native());
        checkMap("gson native utf8", gson_map_utf8_native_reader());
        checkPojo("fastjson2 native string", expected, fastjson2_pojo_string_native());
        checkPojo("fastjson2 native utf8", expected, fastjson2_pojo_utf8_native());
        checkMap("fastjson2 native string", fastjson2_map_string_native());
        checkMap("fastjson2 native utf8", fastjson2_map_utf8_native());
    }

    private static void checkPojo(String label, User expected, Object result) {
        if (!expected.equals(result)) {
            throw new IllegalStateException("POJO result differs: " + label);
        }
    }

    private static void checkMap(String label, Object result) {
        if (!(result instanceof Map)
                || !((Map<?, ?>) result).containsKey("friends")
                || !((Map<?, ?>) result).containsKey("address")) {
            throw new IllegalStateException("Map result differs: " + label);
        }
    }

    // ----- JACKSON2 -----
    @Benchmark
    public Object jackson2_pojo_string_compiled() throws IOException {
        return JACKSON2_COMPILED.readPojo(JSON_DATA);
    }

    @Benchmark
    public Object jackson2_pojo_string_runtime() {
        return JACKSON2_RUNTIME.readNode(JSON_DATA, User.class);
    }

    @Benchmark
    public Object jackson2_pojo_string_native() throws Exception {
        return JACKSON2_NATIVE.readValue(JSON_DATA, User.class);
    }

    @Benchmark
    public Object jackson2_pojo_string_blackbird() throws Exception {
        return JACKSON2_BLACKBIRD.readValue(JSON_DATA, User.class);
    }

    @Benchmark
    public Object jackson2_pojo_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.readPojoUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object jackson2_pojo_utf8_runtime() {
        return JACKSON2_RUNTIME.readNode(JSON_UTF8, User.class);
    }

    @Benchmark
    public Object jackson2_pojo_utf8_native() throws Exception {
        return JACKSON2_NATIVE.readValue(JSON_UTF8, User.class);
    }

    @Benchmark
    public Object jackson2_pojo_utf8_blackbird() throws Exception {
        return JACKSON2_BLACKBIRD.readValue(JSON_UTF8, User.class);
    }

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

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinder {
        @ReadFrom User readPojo(String json) throws IOException;
        @ReadFrom User readPojoUtf8(byte[] json) throws IOException;
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- JACKSON3 -----
    @Benchmark
    public Object jackson3_pojo_string_compiled() throws IOException {
        return JACKSON3_COMPILED.readPojo(JSON_DATA);
    }

    @Benchmark
    public Object jackson3_pojo_string_runtime() {
        return JACKSON3_RUNTIME.readNode(JSON_DATA, User.class);
    }

    @Benchmark
    public Object jackson3_pojo_string_native() throws Exception {
        return JACKSON3_NATIVE.readValue(JSON_DATA, User.class);
    }

    @Benchmark
    public Object jackson3_pojo_string_blackbird() throws Exception {
        return JACKSON3_BLACKBIRD.readValue(JSON_DATA, User.class);
    }

    @Benchmark
    public Object jackson3_pojo_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.readPojoUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object jackson3_pojo_utf8_runtime() {
        return JACKSON3_RUNTIME.readNode(JSON_UTF8, User.class);
    }

    @Benchmark
    public Object jackson3_pojo_utf8_native() throws Exception {
        return JACKSON3_NATIVE.readValue(JSON_UTF8, User.class);
    }

    @Benchmark
    public Object jackson3_pojo_utf8_blackbird() throws Exception {
        return JACKSON3_BLACKBIRD.readValue(JSON_UTF8, User.class);
    }

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

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3CompiledBinder {
        @ReadFrom User readPojo(String json) throws IOException;
        @ReadFrom User readPojoUtf8(byte[] json) throws IOException;
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- GSON -----
    @Benchmark
    public Object gson_pojo_string_compiled() throws IOException {
        return GSON_COMPILED.readPojo(JSON_DATA);
    }

    @Benchmark
    public Object gson_pojo_string_runtime() {
        return GSON_RUNTIME.readNode(JSON_DATA, User.class);
    }

    @Benchmark
    public Object gson_pojo_string_native() throws Exception {
        return GSON_NATIVE.fromJson(JSON_DATA, User.class);
    }

    @Benchmark
    public Object gson_pojo_utf8_compiled() throws IOException {
        return GSON_COMPILED.readPojoUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object gson_pojo_utf8_runtime() {
        return GSON_RUNTIME.readNode(JSON_UTF8, User.class);
    }

    @Benchmark
    public Object gson_pojo_utf8_native_reader() throws Exception {
        return GSON_NATIVE.fromJson(new InputStreamReader(
                new ByteArrayInputStream(JSON_UTF8), StandardCharsets.UTF_8), User.class);
    }

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
        @ReadFrom User readPojo(String json) throws IOException;
        @ReadFrom User readPojoUtf8(byte[] json) throws IOException;
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- FASTJSON2 -----
    @Benchmark
    public Object fastjson2_pojo_string_compiled() throws IOException {
        return FASTJSON2_COMPILED.readPojo(JSON_DATA);
    }

    @Benchmark
    public Object fastjson2_pojo_string_runtime() {
        return FASTJSON2_RUNTIME.readNode(JSON_DATA, User.class);
    }

    @Benchmark
    public Object fastjson2_pojo_string_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(User.class);
        }
    }

    @Benchmark
    public Object fastjson2_pojo_utf8_compiled() throws IOException {
        return FASTJSON2_COMPILED.readPojoUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object fastjson2_pojo_utf8_runtime() {
        return FASTJSON2_RUNTIME.readNode(JSON_UTF8, User.class);
    }

    @Benchmark
    public Object fastjson2_pojo_utf8_native() {
        try (JSONReader reader = JSONReader.of(JSON_UTF8, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(User.class);
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
    public Object fastjson2_map_string_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA, FASTJSON2_NATIVE_CONTEXT)) {
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
    public Object fastjson2_map_utf8_native() {
        try (JSONReader reader = JSONReader.of(JSON_UTF8, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2CompiledBinder {
        @ReadFrom User readPojo(String json) throws IOException;
        @ReadFrom User readPojoUtf8(byte[] json) throws IOException;
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- JSONP -----
    @Benchmark
    public Object jsonp_pojo_string_compiled() throws IOException {
        return JSONP_COMPILED.readPojo(JSON_DATA);
    }

    @Benchmark
    public Object jsonp_pojo_string_runtime() {
        return JSONP_RUNTIME.readNode(JSON_DATA, User.class);
    }

    @Benchmark
    public Object jsonp_pojo_utf8_compiled() throws IOException {
        return JSONP_COMPILED.readPojoUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object jsonp_pojo_utf8_runtime() {
        return JSONP_RUNTIME.readNode(JSON_UTF8, User.class);
    }

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
        @ReadFrom User readPojo(String json) throws IOException;
        @ReadFrom User readPojoUtf8(byte[] json) throws IOException;
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

    // ----- SIMPLE -----
    @Benchmark
    public Object simple_pojo_string_compiled() throws IOException {
        return SIMPLE_COMPILED.readPojo(JSON_DATA);
    }

    @Benchmark
    public Object simple_pojo_string_runtime() {
        return SIMPLE_RUNTIME.readNode(JSON_DATA, User.class);
    }

    @Benchmark
    public Object simple_pojo_utf8_compiled() throws IOException {
        return SIMPLE_COMPILED.readPojoUtf8(JSON_UTF8);
    }

    @Benchmark
    public Object simple_pojo_utf8_runtime() {
        return SIMPLE_RUNTIME.readNode(JSON_UTF8, User.class);
    }

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
        @ReadFrom User readPojo(String json) throws IOException;
        @ReadFrom User readPojoUtf8(byte[] json) throws IOException;
        @ReadFrom Map<String, Object> readMap(String json) throws IOException;
        @ReadFrom Map<String, Object> readMapUtf8(byte[] json) throws IOException;
    }

}
