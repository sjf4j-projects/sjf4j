package org.sjf4j.testbench.binding.handwritten;


import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.json.Json;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.sjf4j.facade.gson.GsonModule;
import org.sjf4j.node.ReflectUtil;
import org.sjf4j.testbench.model.User;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class HandReadBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{HandReadBenchmark.class.getName()});
//        Main.main(new String[]{HandReadBenchmark.class.getName() + ".json_gson"});
    }

    // Same User fixture as HandWriteBenchmark, including its Address and Friend values.
    private static final String JSON_DATA2 = "{\n" +
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

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final ObjectMapper JACKSON2_BLACKBIRD = createBlackbirdJackson2();
    private static final Gson GSON = createNativeGson();
    private static final JSONReader.Context FASTJSON2_NATIVE_CONTEXT = createFastjson2NativeContext();

    static {
        JACKSON2.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static ObjectMapper createBlackbirdJackson2() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new BlackbirdModule());
        return mapper;
    }

    private static Gson createNativeGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setFieldNamingStrategy(field -> {
            String name = ReflectUtil.getExplicitName(field);
            return name != null ? name : field.getName();
        });
        return builder.create();
    }

    private static JSONReader.Context createFastjson2NativeContext() {
        ObjectReaderProvider provider = new ObjectReaderProvider();
        return JSONFactory.createReadContext(new ObjectReaderProvider(), JSONReader.Feature.UseDoubleForDecimals);
    }

    @Setup(Level.Trial)
    public void validateHandwrittenReaders() throws IOException {
        Object jackson2Pojo = json_jackson2_pojo_native();
        validate("Jackson2 POJO", jackson2Pojo, json_jackson2_pojo_handwritten());
        validate("Jackson2 Blackbird POJO", jackson2Pojo, json_jackson2_pojo_blackbird());
        validate("Jackson2 map", json_jackson2_map_native(), json_jackson2_map_handwritten());

        validate("Gson POJO", json_gson_pojo_native(), json_gson_pojo_handwritten());
        validate("Gson map", json_gson_map_native(), json_gson_map_handwritten());

        Object fastjson2Pojo = json_fastjson2_pojo_native();
        validate("Fastjson2 POJO", fastjson2Pojo, json_fastjson2_pojo_handwritten());
        validate("Fastjson2 hash POJO", fastjson2Pojo, json_fastjson2_pojo_handwritten_hash());
        validate("Fastjson2 switch POJO", fastjson2Pojo, json_fastjson2_pojo_handwritten_switch());
        validate("Fastjson2 map", json_fastjson2_map_native(), json_fastjson2_map_handwritten());
    }

    private static void validate(String backend, Object reference, Object candidate) {
        if (!reference.equals(candidate)) {
            throw new IllegalStateException(
                    backend + " output differs from reference: reference=" + reference + ", candidate=" + candidate);
        }
    }


    // ----- Jackson2 baselines -----
    @Benchmark
    public Object json_jackson2_pojo_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_blackbird() throws IOException {
        return JACKSON2_BLACKBIRD.readValue(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_jackson2_pojo_handwritten() throws IOException {
        try (com.fasterxml.jackson.core.JsonParser parser = JACKSON2.getFactory().createParser(JSON_DATA2)) {
            return Jackson2HandPojoReader.readUser(parser);
        }
    }

    @Benchmark
    public Object json_jackson2_pojo_handwritten_v3() throws IOException {
        try (com.fasterxml.jackson.core.JsonParser parser = JACKSON2.getFactory().createParser(JSON_DATA2)) {
            return Jackson2HandPojoReaderV3.readUser(parser);
        }
    }

    @Benchmark
    public Object json_jackson2_pojo_handwritten_v4() throws IOException {
        try (com.fasterxml.jackson.core.JsonParser parser = JACKSON2.getFactory().createParser(JSON_DATA2)) {
            return Jackson2HandPojoReaderV4.readUser(parser);
        }
    }

    @Benchmark
    public Object json_jackson2_map_native() throws IOException {
        return JACKSON2.readValue(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_jackson2_map_handwritten() throws IOException {
        try (com.fasterxml.jackson.core.JsonParser parser = JACKSON2.getFactory().createParser(JSON_DATA2)) {
            return Jackson2HandMapReader.read(parser);
        }
    }


    // ----- Gson baselines -----
    @Benchmark
    public Object json_gson_pojo_native() {
        return GSON.fromJson(JSON_DATA2, User.class);
    }

    @Benchmark
    public Object json_gson_pojo_handwritten() throws IOException {
        try (com.google.gson.stream.JsonReader reader = GSON.newJsonReader(new StringReader(JSON_DATA2))) {
            return GsonHandPojoReader.readUser(reader);
        }
    }

    @Benchmark
    public Object json_gson_pojo_handwritten_v2() throws IOException {
        try (com.google.gson.stream.JsonReader reader = GSON.newJsonReader(new StringReader(JSON_DATA2))) {
            return GsonHandPojoReaderV2.readUser(reader);
        }
    }

    @Benchmark
    public Object json_gson_map_native() {
        return GSON.fromJson(JSON_DATA2, Map.class);
    }

    @Benchmark
    public Object json_gson_map_handwritten() throws IOException {
        try (com.google.gson.stream.JsonReader reader = GSON.newJsonReader(new StringReader(JSON_DATA2))) {
            return GsonHandMapReader.read(reader);
        }
    }


    // ----- Fastjson2 baselines -----
    @Benchmark
    public Object json_fastjson2_pojo_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(User.class);
        }
    }

    /** Direct Fastjson2 parser baseline for the same POJO shape as the native reader. */
    @Benchmark
    public Object json_fastjson2_pojo_handwritten() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return Fastjson2HandPojoReader.readUser(reader);
        }
    }

    /** Direct Fastjson2 parser baseline using native field-name hash dispatch. */
    @Benchmark
    public Object json_fastjson2_pojo_handwritten_hash() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return Fastjson2HashHandReader.readUser(reader);
        }
    }

    @Benchmark
    public Object json_fastjson2_pojo_handwritten_switch() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return Fastjson2SwitchHandReader.readUser(reader);
        }
    }

    @Benchmark
    public Object json_fastjson2_pojo_handwritten_raw() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return Fastjson2RawHandReader.readUser(reader);
        }
    }


    @Benchmark
    public Object json_fastjson2_map_native() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return reader.read(Map.class);
        }
    }

    @Benchmark
    public Object json_fastjson2_map_handwritten() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return Fastjson2HandMapReader.read(reader);
        }
    }

    @Benchmark
    public Object json_fastjson2_map_handwritten_v2() {
        try (JSONReader reader = JSONReader.of(JSON_DATA2, FASTJSON2_NATIVE_CONTEXT)) {
            return Fastjson2HandMapV2Reader.read(reader);
        }
    }


    // ----- JSON-P baselines -----
    @Benchmark
    public Object json_jsonp_map_native() {
        return Json.createReader(new StringReader(JSON_DATA2)).read();
    }

    static class UserHasAny {
        public String name;
        public List<UserHasAny> friends;
        @JsonAnySetter @JsonAnyGetter
        public Map<String, Object> ext = new LinkedHashMap<>();
    }


}
