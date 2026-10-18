package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import org.sjf4j.testbench.model.CreatorUserRecord;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Record equivalent of CompiledReadBenchmark's full User POJO workload.
 * Every timed operation constructs a new parser and a complete object.
 * Compare only equal backend/input/record shapes (compiled vs runtime vs native).
 * The setup checks equality outside the timed measurement.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CreatorReadBenchmark {
    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CreatorReadBenchmark.class.getName()});
    }

    // Exact same JSON content as the mutable-User CompiledReadBenchmark.
    private static final String JSON_DATA = CompiledReadBenchmark.JSON_DATA;
    private static final byte[] JSON_UTF8 = JSON_DATA.getBytes(StandardCharsets.UTF_8);
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

    private static final String JSON_REVERSED = reverseFields(JSON_DATA);
    private static final byte[] JSON_REVERSED_UTF8 = JSON_REVERSED.getBytes(StandardCharsets.UTF_8);

    private static final Jackson2Binder JACKSON2_RUNTIME = new Jackson2Binder();
    private static final Jackson3Binder JACKSON3_RUNTIME = new Jackson3Binder();
    private static final GsonBinder GSON_RUNTIME = new GsonBinder();
    private static final Fastjson2Binder FASTJSON2_RUNTIME = new Fastjson2Binder();
    private static final JsonpBinder JSONP_RUNTIME = new JsonpBinder();
    private static final SimpleJsonBinder SIMPLE_RUNTIME = new SimpleJsonBinder();

    private static final Jackson2CompiledBinder JACKSON2_COMPILED = CompiledInstances.global(Jackson2CompiledBinder.class);
    private static final Jackson3CompiledBinder JACKSON3_COMPILED = CompiledInstances.global(Jackson3CompiledBinder.class);
    private static final GsonCompiledBinder GSON_COMPILED = CompiledInstances.global(GsonCompiledBinder.class);
    private static final Fastjson2CompiledBinder FASTJSON2_COMPILED = CompiledInstances.global(Fastjson2CompiledBinder.class);
    private static final JsonpCompiledBinder JSONP_COMPILED = CompiledInstances.global(JsonpCompiledBinder.class);
    private static final SimpleCompiledBinder SIMPLE_COMPILED = CompiledInstances.global(SimpleCompiledBinder.class);

    private static String reverseFields(String input) {
        try {
            Object root = JACKSON2_NATIVE.readValue(input, Map.class);
            return JACKSON2_NATIVE.writeValueAsString(reverseObjectFields(root));
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static Object reverseObjectFields(Object node) {
        if (node instanceof Map) {
            java.util.List<Map.Entry<?, ?>> entries = new java.util.ArrayList<>(((Map<?, ?>) node).entrySet());
            java.util.Collections.reverse(entries);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : entries) {
                result.put((String) entry.getKey(), reverseObjectFields(entry.getValue()));
            }
            return result;
        }
        if (node instanceof java.util.List) {
            java.util.List<Object> result = new java.util.ArrayList<>();
            for (Object item : (java.util.List<?>) node) result.add(reverseObjectFields(item));
            return result;
        }
        return node;
    }

    @Setup(Level.Trial)
    public void verify() throws Exception {
        CreatorUserRecord expected = JACKSON2_NATIVE.readValue(JSON_DATA, CreatorUserRecord.class);
        check("jackson2_record_string_compiled", expected, jackson2_record_string_compiled());
        check("jackson2_record_string_runtime", expected, jackson2_record_string_runtime());
        check("jackson2_record_string_native", expected, jackson2_record_string_native());
        check("jackson2_record_string_blackbird", expected, jackson2_record_string_blackbird());
        check("jackson2_record_utf8_compiled", expected, jackson2_record_utf8_compiled());
        check("jackson2_record_utf8_runtime", expected, jackson2_record_utf8_runtime());
        check("jackson2_record_utf8_native", expected, jackson2_record_utf8_native());
        check("jackson2_record_utf8_blackbird", expected, jackson2_record_utf8_blackbird());
        check("jackson2_record_reversed_string_compiled", expected, jackson2_record_reversed_string_compiled());
        check("jackson2_record_reversed_string_runtime", expected, jackson2_record_reversed_string_runtime());
        check("jackson2_record_reversed_utf8_compiled", expected, jackson2_record_reversed_utf8_compiled());
        check("jackson2_record_reversed_utf8_runtime", expected, jackson2_record_reversed_utf8_runtime());
        check("jackson3_record_string_compiled", expected, jackson3_record_string_compiled());
        check("jackson3_record_string_runtime", expected, jackson3_record_string_runtime());
        check("jackson3_record_string_native", expected, jackson3_record_string_native());
        check("jackson3_record_string_blackbird", expected, jackson3_record_string_blackbird());
        check("jackson3_record_utf8_compiled", expected, jackson3_record_utf8_compiled());
        check("jackson3_record_utf8_runtime", expected, jackson3_record_utf8_runtime());
        check("jackson3_record_utf8_native", expected, jackson3_record_utf8_native());
        check("jackson3_record_utf8_blackbird", expected, jackson3_record_utf8_blackbird());
        check("jackson3_record_reversed_string_compiled", expected, jackson3_record_reversed_string_compiled());
        check("jackson3_record_reversed_string_runtime", expected, jackson3_record_reversed_string_runtime());
        check("jackson3_record_reversed_utf8_compiled", expected, jackson3_record_reversed_utf8_compiled());
        check("jackson3_record_reversed_utf8_runtime", expected, jackson3_record_reversed_utf8_runtime());
        check("gson_record_string_compiled", expected, gson_record_string_compiled());
        check("gson_record_string_runtime", expected, gson_record_string_runtime());
        check("gson_record_string_native", expected, gson_record_string_native());
        check("gson_record_utf8_compiled", expected, gson_record_utf8_compiled());
        check("gson_record_utf8_runtime", expected, gson_record_utf8_runtime());
        check("gson_record_utf8_native_reader", expected, gson_record_utf8_native_reader());
        check("gson_record_reversed_string_compiled", expected, gson_record_reversed_string_compiled());
        check("gson_record_reversed_string_runtime", expected, gson_record_reversed_string_runtime());
        check("gson_record_reversed_utf8_compiled", expected, gson_record_reversed_utf8_compiled());
        check("gson_record_reversed_utf8_runtime", expected, gson_record_reversed_utf8_runtime());
        check("fastjson2_record_string_compiled", expected, fastjson2_record_string_compiled());
        check("fastjson2_record_string_runtime", expected, fastjson2_record_string_runtime());
        check("fastjson2_record_string_native", expected, fastjson2_record_string_native());
        check("fastjson2_record_utf8_compiled", expected, fastjson2_record_utf8_compiled());
        check("fastjson2_record_utf8_runtime", expected, fastjson2_record_utf8_runtime());
        check("fastjson2_record_utf8_native", expected, fastjson2_record_utf8_native());
        check("fastjson2_record_reversed_string_compiled", expected, fastjson2_record_reversed_string_compiled());
        check("fastjson2_record_reversed_string_runtime", expected, fastjson2_record_reversed_string_runtime());
        check("fastjson2_record_reversed_utf8_compiled", expected, fastjson2_record_reversed_utf8_compiled());
        check("fastjson2_record_reversed_utf8_runtime", expected, fastjson2_record_reversed_utf8_runtime());
        check("jsonp_record_string_compiled", expected, jsonp_record_string_compiled());
        check("jsonp_record_string_runtime", expected, jsonp_record_string_runtime());
        check("jsonp_record_utf8_compiled", expected, jsonp_record_utf8_compiled());
        check("jsonp_record_utf8_runtime", expected, jsonp_record_utf8_runtime());
        check("jsonp_record_reversed_string_compiled", expected, jsonp_record_reversed_string_compiled());
        check("jsonp_record_reversed_string_runtime", expected, jsonp_record_reversed_string_runtime());
        check("jsonp_record_reversed_utf8_compiled", expected, jsonp_record_reversed_utf8_compiled());
        check("jsonp_record_reversed_utf8_runtime", expected, jsonp_record_reversed_utf8_runtime());
        check("simple_record_string_compiled", expected, simple_record_string_compiled());
        check("simple_record_string_runtime", expected, simple_record_string_runtime());
        check("simple_record_utf8_compiled", expected, simple_record_utf8_compiled());
        check("simple_record_utf8_runtime", expected, simple_record_utf8_runtime());
        check("simple_record_reversed_string_compiled", expected, simple_record_reversed_string_compiled());
        check("simple_record_reversed_string_runtime", expected, simple_record_reversed_string_runtime());
        check("simple_record_reversed_utf8_compiled", expected, simple_record_reversed_utf8_compiled());
        check("simple_record_reversed_utf8_runtime", expected, simple_record_reversed_utf8_runtime());
    }

    private static void check(String name, CreatorUserRecord expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Record result differs: " + name + " expected=" + expected + " actual=" + actual);
        }
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_string_compiled() throws Exception {
        return JACKSON2_COMPILED.readRecord(JSON_DATA);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_string_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON2_RUNTIME.readNode(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_string_native() throws Exception {
        return JACKSON2_NATIVE.readValue(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_string_blackbird() throws Exception {
        return JACKSON2_BLACKBIRD.readValue(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_utf8_compiled() throws Exception {
        return JACKSON2_COMPILED.readRecordUtf8(JSON_UTF8);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_utf8_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON2_RUNTIME.readNode(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_utf8_native() throws Exception {
        return JACKSON2_NATIVE.readValue(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_utf8_blackbird() throws Exception {
        return JACKSON2_BLACKBIRD.readValue(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_reversed_string_compiled() throws Exception {
        return JACKSON2_COMPILED.readRecord(JSON_REVERSED);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_reversed_string_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON2_RUNTIME.readNode(JSON_REVERSED, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_reversed_utf8_compiled() throws Exception {
        return JACKSON2_COMPILED.readRecordUtf8(JSON_REVERSED_UTF8);
    }

    @Benchmark
    public CreatorUserRecord jackson2_record_reversed_utf8_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON2_RUNTIME.readNode(JSON_REVERSED_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_string_compiled() throws Exception {
        return JACKSON3_COMPILED.readRecord(JSON_DATA);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_string_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON3_RUNTIME.readNode(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_string_native() throws Exception {
        return JACKSON3_NATIVE.readValue(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_string_blackbird() throws Exception {
        return JACKSON3_BLACKBIRD.readValue(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_utf8_compiled() throws Exception {
        return JACKSON3_COMPILED.readRecordUtf8(JSON_UTF8);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_utf8_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON3_RUNTIME.readNode(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_utf8_native() throws Exception {
        return JACKSON3_NATIVE.readValue(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_utf8_blackbird() throws Exception {
        return JACKSON3_BLACKBIRD.readValue(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_reversed_string_compiled() throws Exception {
        return JACKSON3_COMPILED.readRecord(JSON_REVERSED);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_reversed_string_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON3_RUNTIME.readNode(JSON_REVERSED, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_reversed_utf8_compiled() throws Exception {
        return JACKSON3_COMPILED.readRecordUtf8(JSON_REVERSED_UTF8);
    }

    @Benchmark
    public CreatorUserRecord jackson3_record_reversed_utf8_runtime() throws Exception {
        return (CreatorUserRecord) JACKSON3_RUNTIME.readNode(JSON_REVERSED_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord gson_record_string_compiled() throws Exception {
        return GSON_COMPILED.readRecord(JSON_DATA);
    }

    @Benchmark
    public CreatorUserRecord gson_record_string_runtime() throws Exception {
        return (CreatorUserRecord) GSON_RUNTIME.readNode(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord gson_record_string_native() throws Exception {
        return GSON_NATIVE.fromJson(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord gson_record_utf8_compiled() throws Exception {
        return GSON_COMPILED.readRecordUtf8(JSON_UTF8);
    }

    @Benchmark
    public CreatorUserRecord gson_record_utf8_runtime() throws Exception {
        return (CreatorUserRecord) GSON_RUNTIME.readNode(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord gson_record_utf8_native_reader() throws Exception {
        return GSON_NATIVE.fromJson(new InputStreamReader(new ByteArrayInputStream(JSON_UTF8), StandardCharsets.UTF_8), CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord gson_record_reversed_string_compiled() throws Exception {
        return GSON_COMPILED.readRecord(JSON_REVERSED);
    }

    @Benchmark
    public CreatorUserRecord gson_record_reversed_string_runtime() throws Exception {
        return (CreatorUserRecord) GSON_RUNTIME.readNode(JSON_REVERSED, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord gson_record_reversed_utf8_compiled() throws Exception {
        return GSON_COMPILED.readRecordUtf8(JSON_REVERSED_UTF8);
    }

    @Benchmark
    public CreatorUserRecord gson_record_reversed_utf8_runtime() throws Exception {
        return (CreatorUserRecord) GSON_RUNTIME.readNode(JSON_REVERSED_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_string_compiled() throws IOException {
        return FASTJSON2_COMPILED.readRecord(JSON_DATA);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_string_runtime() throws IOException {
        return (CreatorUserRecord) FASTJSON2_RUNTIME.readNode(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_string_native() throws IOException {
        try (JSONReader reader = JSONReader.of(JSON_DATA, FASTJSON2_NATIVE_CONTEXT)) { return reader.read(CreatorUserRecord.class); }
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_utf8_compiled() throws IOException {
        return FASTJSON2_COMPILED.readRecordUtf8(JSON_UTF8);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_utf8_runtime() throws IOException {
        return (CreatorUserRecord) FASTJSON2_RUNTIME.readNode(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_utf8_native() throws IOException {
        try (JSONReader reader = JSONReader.of(JSON_UTF8, FASTJSON2_NATIVE_CONTEXT)) { return reader.read(CreatorUserRecord.class); }
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_reversed_string_compiled() throws IOException {
        return FASTJSON2_COMPILED.readRecord(JSON_REVERSED);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_reversed_string_runtime() throws IOException {
        return (CreatorUserRecord) FASTJSON2_RUNTIME.readNode(JSON_REVERSED, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_reversed_utf8_compiled() throws IOException {
        return FASTJSON2_COMPILED.readRecordUtf8(JSON_REVERSED_UTF8);
    }

    @Benchmark
    public CreatorUserRecord fastjson2_record_reversed_utf8_runtime() throws IOException {
        return (CreatorUserRecord) FASTJSON2_RUNTIME.readNode(JSON_REVERSED_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_string_compiled() throws IOException {
        return JSONP_COMPILED.readRecord(JSON_DATA);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_string_runtime() throws IOException {
        return (CreatorUserRecord) JSONP_RUNTIME.readNode(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_utf8_compiled() throws IOException {
        return JSONP_COMPILED.readRecordUtf8(JSON_UTF8);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_utf8_runtime() throws IOException {
        return (CreatorUserRecord) JSONP_RUNTIME.readNode(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_reversed_string_compiled() throws IOException {
        return JSONP_COMPILED.readRecord(JSON_REVERSED);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_reversed_string_runtime() throws IOException {
        return (CreatorUserRecord) JSONP_RUNTIME.readNode(JSON_REVERSED, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_reversed_utf8_compiled() throws IOException {
        return JSONP_COMPILED.readRecordUtf8(JSON_REVERSED_UTF8);
    }

    @Benchmark
    public CreatorUserRecord jsonp_record_reversed_utf8_runtime() throws IOException {
        return (CreatorUserRecord) JSONP_RUNTIME.readNode(JSON_REVERSED_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord simple_record_string_compiled() throws IOException {
        return SIMPLE_COMPILED.readRecord(JSON_DATA);
    }

    @Benchmark
    public CreatorUserRecord simple_record_string_runtime() throws IOException {
        return (CreatorUserRecord) SIMPLE_RUNTIME.readNode(JSON_DATA, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord simple_record_utf8_compiled() throws IOException {
        return SIMPLE_COMPILED.readRecordUtf8(JSON_UTF8);
    }

    @Benchmark
    public CreatorUserRecord simple_record_utf8_runtime() throws IOException {
        return (CreatorUserRecord) SIMPLE_RUNTIME.readNode(JSON_UTF8, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord simple_record_reversed_string_compiled() throws IOException {
        return SIMPLE_COMPILED.readRecord(JSON_REVERSED);
    }

    @Benchmark
    public CreatorUserRecord simple_record_reversed_string_runtime() throws IOException {
        return (CreatorUserRecord) SIMPLE_RUNTIME.readNode(JSON_REVERSED, CreatorUserRecord.class);
    }

    @Benchmark
    public CreatorUserRecord simple_record_reversed_utf8_compiled() throws IOException {
        return SIMPLE_COMPILED.readRecordUtf8(JSON_REVERSED_UTF8);
    }

    @Benchmark
    public CreatorUserRecord simple_record_reversed_utf8_runtime() throws IOException {
        return (CreatorUserRecord) SIMPLE_RUNTIME.readNode(JSON_REVERSED_UTF8, CreatorUserRecord.class);
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinder {
        @ReadFrom CreatorUserRecord readRecord(String json) throws IOException;
        @ReadFrom CreatorUserRecord readRecordUtf8(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3CompiledBinder {
        @ReadFrom CreatorUserRecord readRecord(String json) throws IOException;
        @ReadFrom CreatorUserRecord readRecordUtf8(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiledBinder {
        @ReadFrom CreatorUserRecord readRecord(String json) throws IOException;
        @ReadFrom CreatorUserRecord readRecordUtf8(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2CompiledBinder {
        @ReadFrom CreatorUserRecord readRecord(String json) throws IOException;
        @ReadFrom CreatorUserRecord readRecordUtf8(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCompiledBinder {
        @ReadFrom CreatorUserRecord readRecord(String json) throws IOException;
        @ReadFrom CreatorUserRecord readRecordUtf8(byte[] json) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCompiledBinder {
        @ReadFrom CreatorUserRecord readRecord(String json) throws IOException;
        @ReadFrom CreatorUserRecord readRecordUtf8(byte[] json) throws IOException;
    }

}
