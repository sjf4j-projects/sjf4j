package org.sjf4j.testbench.binding.compiled;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import jakarta.json.Json;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson2.binding.Jackson2Writer;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Writer;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.binding.PooledStringWriter;
import org.sjf4j.binding.PooledByteArrayOutputStream;
import org.sjf4j.testbench.binding.handwritten.Jackson2HandPojoSerializedWriter;
import org.sjf4j.testbench.binding.handwritten.Jackson3HandPojoSerializedWriter;
import org.sjf4j.testbench.model.User;
import tools.jackson.databind.json.JsonMapper;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import java.io.IOException;
import java.net.URI;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * CompiledBinder write baseline. Each timed operation includes output-buffer
 * allocation, streaming/serialization, final String or byte[] creation, and
 * owned-writer close. Input User and Map are created only once in static setup.
 *
 * Native Gson bytes is a UTF-8 transcode of its String API (not a direct
 * byte[] API). JSON-P native is a JsonValue tree baseline, not POJO/Map binding.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 15, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class CompiledWriteBenchmark {

    public static void main(String[] args) throws Exception {
//        Main.main(new String[]{CompiledWriteBenchmark.class.getName()});
        Main.main(new String[]{CompiledWriteBenchmark.class.getName() + ".json_jackson._pojo"});
    }


    private static final ObjectMapper JACKSON2_NATIVE = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final ObjectMapper JACKSON2_BLACKBIRD =
            new ObjectMapper().registerModule(new JavaTimeModule())
                    .registerModule(new BlackbirdModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final JsonFactory JACKSON2_DIRECT_FACTORY = JACKSON2_NATIVE.getFactory();
    private static final tools.jackson.core.json.JsonFactory JACKSON3_DIRECT_FACTORY =
            new tools.jackson.core.json.JsonFactory();
    private static final tools.jackson.core.ObjectWriteContext JACKSON3_DIRECT_CONTEXT =
            tools.jackson.core.ObjectWriteContext.empty();
    private static final JsonMapper JACKSON3_NATIVE = new JsonMapper();
    private static final JsonMapper JACKSON3_BLACKBIRD = JsonMapper.builder()
            .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build();
    private static final Gson GSON_NATIVE = new GsonBuilder().serializeNulls()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .create();
    private static final JSONWriter.Context FASTJSON2_NATIVE_CONTEXT =
            new JSONWriter.Context("yyyy-MM-dd'T'HH:mm:ss", JSONWriter.Feature.WriteNulls);

    private static final Jackson2Binder JACKSON2_RUNTIME = new Jackson2Binder();
    private static final Jackson3Binder JACKSON3_RUNTIME = new Jackson3Binder();
    private static final GsonBinder GSON_RUNTIME = new GsonBinder();
    private static final Fastjson2Binder FASTJSON2_RUNTIME = new Fastjson2Binder();
    private static final JsonpBinder JSONP_RUNTIME = new JsonpBinder();
    private static final SimpleJsonBinder SIMPLE_RUNTIME = new SimpleJsonBinder();

    private static final RuntimeContext DIRECT_CONTEXT = RuntimeContext.EMPTY;
    private static final URI URI_VALUE = URI.create("https://example.test/person?id=123");
    private static final User USER;
    private static final Map<String, Object> MAP;
    private static final jakarta.json.JsonObject JSONP_TREE;
    private static final com.fasterxml.jackson.databind.JsonNode EXPECTED_TREE;

    static {
        try {
            String json = CompiledReadBenchmark.JSON_DATA;
            USER = JACKSON2_NATIVE.readValue(json, User.class);
            MAP = JACKSON2_NATIVE.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            EXPECTED_TREE = JACKSON2_NATIVE.readTree(json);
            try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(json))) {
                JSONP_TREE = reader.readObject();
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final Jackson2CompiledBinder JACKSON2_COMPILED =
            CompiledInstances.global(Jackson2CompiledBinder.class);
    private static final Jackson3CompiledBinder JACKSON3_COMPILED =
            CompiledInstances.global(Jackson3CompiledBinder.class);
    private static final GsonCompiledBinder GSON_COMPILED =
            CompiledInstances.global(GsonCompiledBinder.class);
    private static final Fastjson2CompiledBinder FASTJSON2_COMPILED =
            CompiledInstances.global(Fastjson2CompiledBinder.class);
    private static final JsonpCompiledBinder JSONP_COMPILED =
            CompiledInstances.global(JsonpCompiledBinder.class);
    private static final SimpleCompiledBinder SIMPLE_COMPILED =
            CompiledInstances.global(SimpleCompiledBinder.class);

    // Trial-only correctness verification, outside the timed path. Object
    // property order and insignificant JSON formatting may differ.
    // Legacy full-backend verification is kept callable, but the diagnostic
    // benchmarks verify their own paths without making JMH setup dependent on
    // unrelated backends.
    public void verify() throws Exception {
        check("jackson2 pojo compiled string", JACKSON2_COMPILED.writePojo(USER));
        check("jackson2 pojo runtime string", JACKSON2_RUNTIME.writeNodeAsString(USER));
        check("jackson2 pojo compiled utf8", JACKSON2_COMPILED.writePojoUtf8(USER));
        check("jackson2 pojo runtime utf8", JACKSON2_RUNTIME.writeNodeAsBytes(USER));
        check("jackson2 map compiled string", JACKSON2_COMPILED.writeMap(MAP));
        check("jackson2 map runtime string", JACKSON2_RUNTIME.writeNodeAsString(MAP));
        check("jackson2 map compiled utf8", JACKSON2_COMPILED.writeMapUtf8(MAP));
        check("jackson2 map runtime utf8", JACKSON2_RUNTIME.writeNodeAsBytes(MAP));
        check("jackson3 pojo compiled string", JACKSON3_COMPILED.writePojo(USER));
        check("jackson3 pojo runtime string", JACKSON3_RUNTIME.writeNodeAsString(USER));
        check("jackson3 pojo compiled utf8", JACKSON3_COMPILED.writePojoUtf8(USER));
        check("jackson3 pojo runtime utf8", JACKSON3_RUNTIME.writeNodeAsBytes(USER));
        check("jackson3 map compiled string", JACKSON3_COMPILED.writeMap(MAP));
        check("jackson3 map runtime string", JACKSON3_RUNTIME.writeNodeAsString(MAP));
        check("jackson3 map compiled utf8", JACKSON3_COMPILED.writeMapUtf8(MAP));
        check("jackson3 map runtime utf8", JACKSON3_RUNTIME.writeNodeAsBytes(MAP));
        check("gson pojo compiled string", GSON_COMPILED.writePojo(USER));
        check("gson pojo runtime string", GSON_RUNTIME.writeNodeAsString(USER));
        check("gson pojo compiled utf8", GSON_COMPILED.writePojoUtf8(USER));
        check("gson pojo runtime utf8", GSON_RUNTIME.writeNodeAsBytes(USER));
        check("gson map compiled string", GSON_COMPILED.writeMap(MAP));
        check("gson map runtime string", GSON_RUNTIME.writeNodeAsString(MAP));
        check("gson map compiled utf8", GSON_COMPILED.writeMapUtf8(MAP));
        check("gson map runtime utf8", GSON_RUNTIME.writeNodeAsBytes(MAP));
        check("fastjson2 pojo compiled string", FASTJSON2_COMPILED.writePojo(USER));
        check("fastjson2 pojo runtime string", FASTJSON2_RUNTIME.writeNodeAsString(USER));
        check("fastjson2 pojo compiled utf8", FASTJSON2_COMPILED.writePojoUtf8(USER));
        check("fastjson2 pojo runtime utf8", FASTJSON2_RUNTIME.writeNodeAsBytes(USER));
        check("fastjson2 map compiled string", FASTJSON2_COMPILED.writeMap(MAP));
        check("fastjson2 map runtime string", FASTJSON2_RUNTIME.writeNodeAsString(MAP));
        check("fastjson2 map compiled utf8", FASTJSON2_COMPILED.writeMapUtf8(MAP));
        check("fastjson2 map runtime utf8", FASTJSON2_RUNTIME.writeNodeAsBytes(MAP));
        check("jsonp pojo compiled string", JSONP_COMPILED.writePojo(USER));
        check("jsonp pojo runtime string", JSONP_RUNTIME.writeNodeAsString(USER));
        check("jsonp pojo compiled utf8", JSONP_COMPILED.writePojoUtf8(USER));
        check("jsonp pojo runtime utf8", JSONP_RUNTIME.writeNodeAsBytes(USER));
        check("jsonp map compiled string", JSONP_COMPILED.writeMap(MAP));
        check("jsonp map runtime string", JSONP_RUNTIME.writeNodeAsString(MAP));
        check("jsonp map compiled utf8", JSONP_COMPILED.writeMapUtf8(MAP));
        check("jsonp map runtime utf8", JSONP_RUNTIME.writeNodeAsBytes(MAP));
        check("simple pojo compiled string", SIMPLE_COMPILED.writePojo(USER));
        check("simple pojo runtime string", SIMPLE_RUNTIME.writeNodeAsString(USER));
        check("simple pojo compiled utf8", SIMPLE_COMPILED.writePojoUtf8(USER));
        check("simple pojo runtime utf8", SIMPLE_RUNTIME.writeNodeAsBytes(USER));
        check("simple map compiled string", SIMPLE_COMPILED.writeMap(MAP));
        check("simple map runtime string", SIMPLE_RUNTIME.writeNodeAsString(MAP));
        check("simple map compiled utf8", SIMPLE_COMPILED.writeMapUtf8(MAP));
        check("simple map runtime utf8", SIMPLE_RUNTIME.writeNodeAsBytes(MAP));

        check("jackson2 native pojo string", json_jackson2_pojo_string_native());
        check("jackson2 blackbird pojo string", json_jackson2_pojo_string_blackbird());
        check("jackson2 direct wrapped pojo string", json_jackson2_pojo_string_direct_wrapped());
        check("jackson2 direct pojo string", json_jackson2_pojo_string_direct());
        check("jackson2 native pojo utf8", json_jackson2_pojo_utf8_native());
        check("jackson2 blackbird pojo utf8", json_jackson2_pojo_utf8_blackbird());
        check("jackson2 direct wrapped pojo utf8", json_jackson2_pojo_utf8_direct_wrapped());
        check("jackson2 direct pojo utf8", json_jackson2_pojo_utf8_direct());
        check("jackson2 native map string", json_jackson2_map_string_native());
        check("jackson2 native map utf8", json_jackson2_map_utf8_native());
        check("jackson3 native pojo string", json_jackson3_pojo_string_native());
        check("jackson3 blackbird pojo string", json_jackson3_pojo_string_blackbird());
        check("jackson3 direct wrapped pojo string", json_jackson3_pojo_string_direct_wrapped());
        check("jackson3 direct pojo string", json_jackson3_pojo_string_direct());
        check("jackson3 native pojo utf8", json_jackson3_pojo_utf8_native());
        check("jackson3 blackbird pojo utf8", json_jackson3_pojo_utf8_blackbird());
        check("jackson3 direct wrapped pojo utf8", json_jackson3_pojo_utf8_direct_wrapped());
        check("jackson3 direct pojo utf8", json_jackson3_pojo_utf8_direct());
        check("jackson3 native map string", json_jackson3_map_string_native());
        check("jackson3 native map utf8", json_jackson3_map_utf8_native());
        check("gson native pojo string", json_gson_pojo_string_native());
        check("gson native map string", json_gson_map_string_native());
        check("fastjson2 native pojo string", json_fastjson2_pojo_string_native());
        check("fastjson2 native pojo utf8", json_fastjson2_pojo_utf8_native());
        check("fastjson2 native map string", json_fastjson2_map_string_native());
        check("fastjson2 native map utf8", json_fastjson2_map_utf8_native());
    }

    @Setup(Level.Trial)
    public void verifyDirectWrapped() throws Exception {
        checkUri("jackson2 uri compiled", json_jackson2_uri_compiled());
        checkUri("jackson2 uri runtime", json_jackson2_uri_runtime());
        checkUri("jackson2 uri direct", json_jackson2_uri_direct());
        checkUri("jackson3 uri compiled", json_jackson3_uri_compiled());
        checkUri("jackson3 uri runtime", json_jackson3_uri_runtime());
        checkUri("jackson3 uri direct", json_jackson3_uri_direct());
        check("jackson2 direct wrapped string", json_jackson2_pojo_string_direct_wrapped());
        check("jackson2 direct wrapped utf8", json_jackson2_pojo_utf8_direct_wrapped());
        check("jackson3 direct wrapped string", json_jackson3_pojo_string_direct_wrapped());
        check("jackson3 direct wrapped utf8", json_jackson3_pojo_utf8_direct_wrapped());

        // All three paths should now emit exactly the same JSON bytes (not
        // merely equal object trees): property order, null policy and escaping.
        checkExact("jackson2 direct string", JACKSON2_COMPILED.writePojo(USER),
                json_jackson2_pojo_string_direct());
        checkExact("jackson2 direct wrapped string", JACKSON2_COMPILED.writePojo(USER),
                json_jackson2_pojo_string_direct_wrapped());
        checkExact("jackson2 direct utf8", JACKSON2_COMPILED.writePojoUtf8(USER),
                json_jackson2_pojo_utf8_direct());
        checkExact("jackson2 direct wrapped utf8", JACKSON2_COMPILED.writePojoUtf8(USER),
                json_jackson2_pojo_utf8_direct_wrapped());
        checkExact("jackson3 direct string", JACKSON3_COMPILED.writePojo(USER),
                json_jackson3_pojo_string_direct());
        checkExact("jackson3 direct wrapped string", JACKSON3_COMPILED.writePojo(USER),
                json_jackson3_pojo_string_direct_wrapped());
        checkExact("jackson3 direct utf8", JACKSON3_COMPILED.writePojoUtf8(USER),
                json_jackson3_pojo_utf8_direct());
        checkExact("jackson3 direct wrapped utf8", JACKSON3_COMPILED.writePojoUtf8(USER),
                json_jackson3_pojo_utf8_direct_wrapped());

        verifyNullPolicyParity();
    }

    private static void checkExact(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(label + " output differs from Compiled at char "
                    + firstDifference(expected, actual));
        }
    }

    private static void checkExact(String label, byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new IllegalStateException(label + " bytes differ from Compiled");
        }
    }

    private static int firstDifference(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) if (a.charAt(i) != b.charAt(i)) return i;
        return n;
    }

    private static void verifyNullPolicyParity() throws IOException {
        User example = new User();
        example.setUsername("present");
        example.setAddress(new org.sjf4j.testbench.model.Address(
                null, "City", null, null, null));
        example.setTags(Arrays.asList("tag", null));
        example.setFriends(Arrays.asList(
                new org.sjf4j.testbench.model.Friend(7L, null, 9L, true), null));

        for (boolean includeNulls : new boolean[]{true, false}) {
            RuntimeContext ctx = new RuntimeContext(includeNulls);
            String j2 = CompiledInstances.create(Jackson2CompiledBinder.class, ctx)
                    .writePojo(example);
            String j3 = CompiledInstances.create(Jackson3CompiledBinder.class, ctx)
                    .writePojo(example);

            java.io.StringWriter out2 = new java.io.StringWriter();
            try (JsonGenerator g = JACKSON2_DIRECT_FACTORY.createGenerator(out2)) {
                Jackson2HandPojoSerializedWriter.writeUser(g, example, ctx);
            }
            checkExact("jackson2 includeNulls=" + includeNulls, j2, out2.toString());

            java.io.StringWriter out3 = new java.io.StringWriter();
            try (tools.jackson.core.JsonGenerator g = JACKSON3_DIRECT_FACTORY.createGenerator(
                    JACKSON3_DIRECT_CONTEXT, out3)) {
                Jackson3HandPojoSerializedWriter.writeUser(g, example, ctx);
            }
            checkExact("jackson3 includeNulls=" + includeNulls, j3, out3.toString());

            com.fasterxml.jackson.databind.JsonNode tree = JACKSON2_NATIVE.readTree(j2);
            if (!tree.path("tags").get(1).isNull()
                    || !tree.path("friends").get(1).isNull()
                    || tree.path("address").path("city").asText().isEmpty()) {
                throw new IllegalStateException("Array null elements or address lost");
            }
            if (!includeNulls && (tree.has("email") || tree.path("address").has("street")
                    || tree.path("friends").get(0).has("name"))) {
                throw new IllegalStateException("Null-valued properties must be omitted");
            }
        }
    }

    private static void checkUri(String label, String json) throws IOException {
        if (!URI_VALUE.toString().equals(JACKSON2_NATIVE.readTree(json).asText())) {
            throw new IllegalStateException("URI JSON differs: " + label);
        }
    }

    private static void check(String label, String json) throws IOException {
        if (!EXPECTED_TREE.equals(JACKSON2_NATIVE.readTree(json))) {
            throw new IllegalStateException("JSON differs: " + label);
        }
    }

    private static void check(String label, byte[] utf8) throws IOException {
        if (!EXPECTED_TREE.equals(JACKSON2_NATIVE.readTree(utf8))) {
            throw new IllegalStateException("UTF-8 JSON differs: " + label);
        }
    }

    // Scalar URI case isolates streaming Codec dispatch from POJO member writes.
    @Benchmark
    public String json_jackson2_uri_compiled() throws IOException {
        return JACKSON2_COMPILED.writeUri(URI_VALUE);
    }

    @Benchmark
    public String json_jackson2_uri_runtime() {
        return JACKSON2_RUNTIME.writeNodeAsString(URI_VALUE);
    }

    @Benchmark
    public String json_jackson2_uri_direct() throws IOException {
        BufferRecycler recycler = JACKSON2_DIRECT_FACTORY._getBufferRecycler();
        SegmentedStringWriter output = new SegmentedStringWriter(recycler);
        try {
            try (JsonGenerator g = JACKSON2_DIRECT_FACTORY.createGenerator(output)) {
                g.writeString(URI_VALUE.toString());
            }
            return output.getAndClear();
        } finally {
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public String json_jackson3_uri_compiled() throws IOException {
        return JACKSON3_COMPILED.writeUri(URI_VALUE);
    }

    @Benchmark
    public String json_jackson3_uri_runtime() {
        return JACKSON3_RUNTIME.writeNodeAsString(URI_VALUE);
    }

    @Benchmark
    public String json_jackson3_uri_direct() throws IOException {
        tools.jackson.core.util.BufferRecycler recycler =
                JACKSON3_DIRECT_FACTORY._getBufferRecycler();
        tools.jackson.core.io.SegmentedStringWriter output =
                new tools.jackson.core.io.SegmentedStringWriter(recycler);
        try {
            try (tools.jackson.core.JsonGenerator g = JACKSON3_DIRECT_FACTORY.createGenerator(
                    JACKSON3_DIRECT_CONTEXT, output)) {
                g.writeString(URI_VALUE.toString());
            }
            return output.getAndClear();
        } finally {
            recycler.releaseToPool();
        }
    }

    // ----- JACKSON2 -----
    @Benchmark
    public Object json_jackson2_pojo_string_compiled() throws IOException {
        return JACKSON2_COMPILED.writePojo(USER);
    }

    @Benchmark
    public Object json_jackson2_pojo_string_runtime() {
        return JACKSON2_RUNTIME.writeNodeAsString(USER);
    }

    @Benchmark
    public String json_jackson2_pojo_string_native() throws Exception {
        return JACKSON2_NATIVE.writeValueAsString(USER);
    }

    @Benchmark
    public String json_jackson2_pojo_string_blackbird() throws Exception {
        return JACKSON2_BLACKBIRD.writeValueAsString(USER);
    }

    @Benchmark
    public byte[] json_jackson2_pojo_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.writePojoUtf8(USER);
    }

    @Benchmark
    public byte[] json_jackson2_pojo_utf8_runtime() {
        return JACKSON2_RUNTIME.writeNodeAsBytes(USER);
    }

    @Benchmark
    public byte[] json_jackson2_pojo_utf8_native() throws Exception {
        return JACKSON2_NATIVE.writeValueAsBytes(USER);
    }

    @Benchmark
    public byte[] json_jackson2_pojo_utf8_blackbird() throws Exception {
        return JACKSON2_BLACKBIRD.writeValueAsBytes(USER);
    }

    @Benchmark
    public String json_jackson2_map_string_compiled() throws IOException {
        return JACKSON2_COMPILED.writeMap(MAP);
    }

    @Benchmark
    public String json_jackson2_map_string_runtime() {
        return JACKSON2_RUNTIME.writeNodeAsString(MAP);
    }

    @Benchmark
    public String json_jackson2_map_string_native() throws Exception {
        return JACKSON2_NATIVE.writeValueAsString(MAP);
    }

    @Benchmark
    public byte[] json_jackson2_map_utf8_compiled() throws IOException {
        return JACKSON2_COMPILED.writeMapUtf8(MAP);
    }

    @Benchmark
    public byte[] json_jackson2_map_utf8_runtime() {
        return JACKSON2_RUNTIME.writeNodeAsBytes(MAP);
    }

    @Benchmark
    public byte[] json_jackson2_map_utf8_native() throws Exception {
        return JACKSON2_NATIVE.writeValueAsBytes(MAP);
    }


    // Direct handwritten Jackson generator baseline: same User input, same output
    // materialization and pooled buffers as compiled write, no SJF4J emitter.
    @Benchmark
    public String json_jackson2_pojo_string_direct() throws IOException {
        BufferRecycler recycler = JACKSON2_DIRECT_FACTORY._getBufferRecycler();
        SegmentedStringWriter output = new SegmentedStringWriter(recycler);
        try {
            try (JsonGenerator g = JACKSON2_DIRECT_FACTORY.createGenerator(output)) {
                Jackson2HandPojoSerializedWriter.writeUser(g, USER, DIRECT_CONTEXT);
            }
            return output.getAndClear();
        } finally {
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public byte[] json_jackson2_pojo_utf8_direct() throws IOException {
        BufferRecycler recycler = JACKSON2_DIRECT_FACTORY._getBufferRecycler();
        ByteArrayBuilder output = new ByteArrayBuilder(recycler);
        try {
            try (JsonGenerator g = JACKSON2_DIRECT_FACTORY.createGenerator(
                    output, JsonEncoding.UTF8)) {
                Jackson2HandPojoSerializedWriter.writeUser(g, USER, DIRECT_CONTEXT);
            }
            return output.toByteArray();
        } finally {
            output.release();
            recycler.releaseToPool();
        }
    }

    // Diagnostic: identical handwritten field emission as Direct, but the
    // same SJF4J Writer creation, flush and release lifecycle as Compiled.
    @Benchmark
    public String json_jackson2_pojo_string_direct_wrapped() throws IOException {
        PooledStringWriter output = new PooledStringWriter();
        try (Jackson2Writer writer = JACKSON2_RUNTIME.createWriter(output)) {
            Jackson2HandPojoSerializedWriter.writeUser(writer.nativeWriter(), USER, DIRECT_CONTEXT);
            writer.flush();
            return output.toString();
        } finally {
            output.recycle();
        }
    }

    @Benchmark
    public byte[] json_jackson2_pojo_utf8_direct_wrapped() throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try (Jackson2Writer writer = JACKSON2_RUNTIME.createWriter(output)) {
            Jackson2HandPojoSerializedWriter.writeUser(writer.nativeWriter(), USER, DIRECT_CONTEXT);
            writer.flush();
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2CompiledBinder {
        @WriteTo String writePojo(User value) throws IOException;
        @WriteTo byte[] writePojoUtf8(User value) throws IOException;
        @WriteTo String writeMap(Map<String, Object> value) throws IOException;
        @WriteTo String writeUri(URI value) throws IOException;
        @WriteTo byte[] writeMapUtf8(Map<String, Object> value) throws IOException;
    }

    // ----- JACKSON3 -----
    @Benchmark
    public String json_jackson3_pojo_string_compiled() throws IOException {
        return JACKSON3_COMPILED.writePojo(USER);
    }

    @Benchmark
    public String json_jackson3_pojo_string_runtime() {
        return JACKSON3_RUNTIME.writeNodeAsString(USER);
    }

    @Benchmark
    public String json_jackson3_pojo_string_native() throws Exception {
        return JACKSON3_NATIVE.writeValueAsString(USER);
    }

    @Benchmark
    public String json_jackson3_pojo_string_blackbird() throws Exception {
        return JACKSON3_BLACKBIRD.writeValueAsString(USER);
    }

    @Benchmark
    public byte[] json_jackson3_pojo_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.writePojoUtf8(USER);
    }

    @Benchmark
    public byte[] json_jackson3_pojo_utf8_runtime() {
        return JACKSON3_RUNTIME.writeNodeAsBytes(USER);
    }

    @Benchmark
    public byte[] json_jackson3_pojo_utf8_native() throws Exception {
        return JACKSON3_NATIVE.writeValueAsBytes(USER);
    }

    @Benchmark
    public byte[] json_jackson3_pojo_utf8_blackbird() throws Exception {
        return JACKSON3_BLACKBIRD.writeValueAsBytes(USER);
    }

    @Benchmark
    public String json_jackson3_map_string_compiled() throws IOException {
        return JACKSON3_COMPILED.writeMap(MAP);
    }

    @Benchmark
    public String json_jackson3_map_string_runtime() {
        return JACKSON3_RUNTIME.writeNodeAsString(MAP);
    }

    @Benchmark
    public String json_jackson3_map_string_native() throws Exception {
        return JACKSON3_NATIVE.writeValueAsString(MAP);
    }

    @Benchmark
    public byte[] json_jackson3_map_utf8_compiled() throws IOException {
        return JACKSON3_COMPILED.writeMapUtf8(MAP);
    }

    @Benchmark
    public byte[] json_jackson3_map_utf8_runtime() {
        return JACKSON3_RUNTIME.writeNodeAsBytes(MAP);
    }

    @Benchmark
    public byte[] json_jackson3_map_utf8_native() throws Exception {
        return JACKSON3_NATIVE.writeValueAsBytes(MAP);
    }


    // Jackson3 counterpart; use the same direct handwritten token sequence.
    @Benchmark
    public String json_jackson3_pojo_string_direct() throws IOException {
        tools.jackson.core.util.BufferRecycler recycler =
                JACKSON3_DIRECT_FACTORY._getBufferRecycler();
        tools.jackson.core.io.SegmentedStringWriter output =
                new tools.jackson.core.io.SegmentedStringWriter(recycler);
        try {
            try (tools.jackson.core.JsonGenerator g = JACKSON3_DIRECT_FACTORY.createGenerator(
                    JACKSON3_DIRECT_CONTEXT, output)) {
                Jackson3HandPojoSerializedWriter.writeUser(g, USER, DIRECT_CONTEXT);
            }
            return output.getAndClear();
        } finally {
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public byte[] json_jackson3_pojo_utf8_direct() throws IOException {
        tools.jackson.core.util.BufferRecycler recycler =
                JACKSON3_DIRECT_FACTORY._getBufferRecycler();
        tools.jackson.core.util.ByteArrayBuilder output =
                new tools.jackson.core.util.ByteArrayBuilder(recycler);
        try {
            try (tools.jackson.core.JsonGenerator g = JACKSON3_DIRECT_FACTORY.createGenerator(
                    JACKSON3_DIRECT_CONTEXT, output)) {
                Jackson3HandPojoSerializedWriter.writeUser(g, USER, DIRECT_CONTEXT);
            }
            return output.toByteArray();
        } finally {
            output.release();
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public String json_jackson3_pojo_string_direct_wrapped() throws IOException {
        PooledStringWriter output = new PooledStringWriter();
        try (Jackson3Writer writer = JACKSON3_RUNTIME.createWriter(output)) {
            Jackson3HandPojoSerializedWriter.writeUser(writer.nativeWriter(), USER, DIRECT_CONTEXT);
            writer.flush();
            return output.toString();
        } finally {
            output.recycle();
        }
    }

    @Benchmark
    public byte[] json_jackson3_pojo_utf8_direct_wrapped() throws IOException {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        try (Jackson3Writer writer = JACKSON3_RUNTIME.createWriter(output)) {
            Jackson3HandPojoSerializedWriter.writeUser(writer.nativeWriter(), USER, DIRECT_CONTEXT);
            writer.flush();
            return output.toByteArray();
        } finally {
            output.recycle();
        }
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3CompiledBinder {
        @WriteTo String writePojo(User value) throws IOException;
        @WriteTo byte[] writePojoUtf8(User value) throws IOException;
        @WriteTo String writeMap(Map<String, Object> value) throws IOException;
        @WriteTo String writeUri(URI value) throws IOException;
        @WriteTo byte[] writeMapUtf8(Map<String, Object> value) throws IOException;
    }

    // ----- GSON -----
    @Benchmark
    public String json_gson_pojo_string_compiled() throws IOException {
        return GSON_COMPILED.writePojo(USER);
    }

    @Benchmark
    public String json_gson_pojo_string_runtime() {
        return GSON_RUNTIME.writeNodeAsString(USER);
    }

    @Benchmark
    public String json_gson_pojo_string_native() throws Exception {
        return GSON_NATIVE.toJson(USER);
    }

    @Benchmark
    public byte[] json_gson_pojo_utf8_compiled() throws IOException {
        return GSON_COMPILED.writePojoUtf8(USER);
    }

    @Benchmark
    public byte[] json_gson_pojo_utf8_runtime() {
        return GSON_RUNTIME.writeNodeAsBytes(USER);
    }

    @Benchmark
    public String json_gson_map_string_compiled() throws IOException {
        return GSON_COMPILED.writeMap(MAP);
    }

    @Benchmark
    public String json_gson_map_string_runtime() {
        return GSON_RUNTIME.writeNodeAsString(MAP);
    }

    @Benchmark
    public String json_gson_map_string_native() throws Exception {
        return GSON_NATIVE.toJson(MAP);
    }

    @Benchmark
    public byte[] json_gson_map_utf8_compiled() throws IOException {
        return GSON_COMPILED.writeMapUtf8(MAP);
    }

    @Benchmark
    public byte[] json_gson_map_utf8_runtime() {
        return GSON_RUNTIME.writeNodeAsBytes(MAP);
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiledBinder {
        @WriteTo String writePojo(User value) throws IOException;
        @WriteTo byte[] writePojoUtf8(User value) throws IOException;
        @WriteTo String writeMap(Map<String, Object> value) throws IOException;
        @WriteTo byte[] writeMapUtf8(Map<String, Object> value) throws IOException;
    }

    // ----- FASTJSON2 -----
    @Benchmark
    public String json_fastjson2_pojo_string_compiled() throws IOException {
        return FASTJSON2_COMPILED.writePojo(USER);
    }

    @Benchmark
    public String json_fastjson2_pojo_string_runtime() {
        return FASTJSON2_RUNTIME.writeNodeAsString(USER);
    }

    @Benchmark
    public String json_fastjson2_pojo_string_native() throws Exception {
        return JSON.toJSONString(USER, FASTJSON2_NATIVE_CONTEXT);
    }

    @Benchmark
    public byte[] json_fastjson2_pojo_utf8_compiled() throws IOException {
        return FASTJSON2_COMPILED.writePojoUtf8(USER);
    }

    @Benchmark
    public byte[] json_fastjson2_pojo_utf8_runtime() {
        return FASTJSON2_RUNTIME.writeNodeAsBytes(USER);
    }

    @Benchmark
    public byte[] json_fastjson2_pojo_utf8_native() throws Exception {
        return JSON.toJSONBytes(USER, StandardCharsets.UTF_8, FASTJSON2_NATIVE_CONTEXT);
    }

    @Benchmark
    public String json_fastjson2_map_string_compiled() throws IOException {
        return FASTJSON2_COMPILED.writeMap(MAP);
    }

    @Benchmark
    public String json_fastjson2_map_string_runtime() {
        return FASTJSON2_RUNTIME.writeNodeAsString(MAP);
    }

    @Benchmark
    public String json_fastjson2_map_string_native() throws Exception {
        return JSON.toJSONString(MAP, FASTJSON2_NATIVE_CONTEXT);
    }

    @Benchmark
    public byte[] json_fastjson2_map_utf8_compiled() throws IOException {
        return FASTJSON2_COMPILED.writeMapUtf8(MAP);
    }

    @Benchmark
    public byte[] json_fastjson2_map_utf8_runtime() {
        return FASTJSON2_RUNTIME.writeNodeAsBytes(MAP);
    }

    @Benchmark
    public byte[] json_fastjson2_map_utf8_native() throws Exception {
        return JSON.toJSONBytes(MAP, StandardCharsets.UTF_8, FASTJSON2_NATIVE_CONTEXT);
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2CompiledBinder {
        @WriteTo String writePojo(User value) throws IOException;
        @WriteTo byte[] writePojoUtf8(User value) throws IOException;
        @WriteTo String writeMap(Map<String, Object> value) throws IOException;
        @WriteTo byte[] writeMapUtf8(Map<String, Object> value) throws IOException;
    }

    // ----- JSONP -----
    @Benchmark
    public String json_jsonp_pojo_string_compiled() throws IOException {
        return JSONP_COMPILED.writePojo(USER);
    }

    @Benchmark
    public String json_jsonp_pojo_string_runtime() {
        return JSONP_RUNTIME.writeNodeAsString(USER);
    }

    @Benchmark
    public byte[] json_jsonp_pojo_utf8_compiled() throws IOException {
        return JSONP_COMPILED.writePojoUtf8(USER);
    }

    @Benchmark
    public byte[] json_jsonp_pojo_utf8_runtime() {
        return JSONP_RUNTIME.writeNodeAsBytes(USER);
    }

    @Benchmark
    public String json_jsonp_map_string_compiled() throws IOException {
        return JSONP_COMPILED.writeMap(MAP);
    }

    @Benchmark
    public String json_jsonp_map_string_runtime() {
        return JSONP_RUNTIME.writeNodeAsString(MAP);
    }

    @Benchmark
    public byte[] json_jsonp_map_utf8_compiled() throws IOException {
        return JSONP_COMPILED.writeMapUtf8(MAP);
    }

    @Benchmark
    public byte[] json_jsonp_map_utf8_runtime() {
        return JSONP_RUNTIME.writeNodeAsBytes(MAP);
    }

    // JSON-P native handles its immutable tree, not User or java.util.Map.
    @Benchmark
    public String json_jsonp_tree_string_native() {
        java.io.StringWriter output = new java.io.StringWriter();
        try (jakarta.json.JsonWriter writer = Json.createWriter(output)) {
            writer.write(JSONP_TREE);
        }
        return output.toString();
    }

    @Benchmark
    public byte[] json_jsonp_tree_utf8_native() {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (jakarta.json.JsonWriter writer = Json.createWriter(output)) {
            writer.write(JSONP_TREE);
        }
        return output.toByteArray();
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCompiledBinder {
        @WriteTo String writePojo(User value) throws IOException;
        @WriteTo byte[] writePojoUtf8(User value) throws IOException;
        @WriteTo String writeMap(Map<String, Object> value) throws IOException;
        @WriteTo byte[] writeMapUtf8(Map<String, Object> value) throws IOException;
    }

    // ----- SIMPLE -----
    @Benchmark
    public String json_simple_pojo_string_compiled() throws IOException {
        return SIMPLE_COMPILED.writePojo(USER);
    }

    @Benchmark
    public String json_simple_pojo_string_runtime() {
        return SIMPLE_RUNTIME.writeNodeAsString(USER);
    }

    @Benchmark
    public byte[] json_simple_pojo_utf8_compiled() throws IOException {
        return SIMPLE_COMPILED.writePojoUtf8(USER);
    }

    @Benchmark
    public byte[] json_simple_pojo_utf8_runtime() {
        return SIMPLE_RUNTIME.writeNodeAsBytes(USER);
    }

    @Benchmark
    public String json_simple_map_string_compiled() throws IOException {
        return SIMPLE_COMPILED.writeMap(MAP);
    }

    @Benchmark
    public String json_simple_map_string_runtime() {
        return SIMPLE_RUNTIME.writeNodeAsString(MAP);
    }

    @Benchmark
    public Object json_simple_map_utf8_compiled() throws IOException {
        return SIMPLE_COMPILED.writeMapUtf8(MAP);
    }

    @Benchmark
    public byte[] json_simple_map_utf8_runtime() {
        return SIMPLE_RUNTIME.writeNodeAsBytes(MAP);
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCompiledBinder {
        @WriteTo String writePojo(User value) throws IOException;
        @WriteTo byte[] writePojoUtf8(User value) throws IOException;
        @WriteTo String writeMap(Map<String, Object> value) throws IOException;
        @WriteTo byte[] writeMapUtf8(Map<String, Object> value) throws IOException;
    }

}
