package org.sjf4j.handwritten;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Threads(1)
@State(Scope.Thread)
public class HandWriteBenchmark {

    private static final ObjectMapper JACKSON2 = new ObjectMapper();
    private static final JsonFactory JACKSON2_FACTORY = JACKSON2.getFactory();
    private static final Gson GSON = createNativeGson();
    private static final JSONWriter.Context FASTJSON2_NATIVE_CONTEXT =
            JSONFactory.createWriteContext(JSONWriter.Feature.WriteNulls);
    private static final User USER = createUser();

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{HandWriteBenchmark.class.getName()});
//        Main.main(new String[]{HandWriteBenchmark.class.getName() + ".json_fastjson2"});

    }

    private static Gson createNativeGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        builder.serializeNulls();
        builder.setFieldNamingStrategy(field -> {
            String name = ReflectUtil.getExplicitName(field);
            return name != null ? name : field.getName();
        });
        return builder.create();
    }

    private static User createUser() {
        User user = new User();
        user.setId(839201L);
        user.setUsername("alice.builder");
        user.setEmail(null);
        user.setDisplayName("Alice \"the Builder\"\\n\tLine\r\b");
        user.setPasswordHash("$2a$12$abc\\def");
        user.setBio("Builds\tfast JSON\rwith control\bchars");
        user.setWebsite("https://example.test/a?b=1");
        user.setDepartment("Platform Engineering");
        user.setCreatedAt(1700000000123L);
        user.setUpdatedAt(1701234567890L);
        user.setLoginCount(421);
        user.setReputation(9876543210L);
        user.setActive(true);
        user.setVerified(true);
        user.setAdmin(false);
        user.setSuspended(false);
        user.setScore(98.75d);
        user.setLatitude(37.7749d);
        user.setLongitude(-122.4194d);
        user.setAge(34);
        user.setAddress(new Address("1 Main St", "San Francisco", "CA", "94105", "US"));
        ArrayList<String> tags = new ArrayList<String>();
        tags.add("tag-0-value\\\"\n");
        tags.add(null);
        user.setTags(tags);
        ArrayList<Friend> friends = new ArrayList<Friend>();
        friends.add(new Friend(1000L, "Bill\\Backslash", 1600000000000L, true));
        friends.add(null);
        friends.add(new Friend(1001L, "Cindy\nLine\t\r\b\\\"", 1600000000001L, false));
        user.setFriends(friends);
        return user;
    }

    @Setup(Level.Trial)
    public void validateHandwrittenWriters() throws IOException {
        validate("Jackson2 native String fromBytes", json_jackson2_string_native(),
                json_jackson2_string_native_fromBytes());
        validate("Jackson2 String", json_jackson2_string_native(), json_jackson2_string_handwritten());
        validate("Jackson2 String fromBytes", json_jackson2_string_native(),
                json_jackson2_string_handwritten_fromBytes());
        validate("Jackson2 String serialized names", json_jackson2_string_native(),
                json_jackson2_string_handwritten_serialized());
        validate("Jackson2 String fromBytes serialized names", json_jackson2_string_native(),
                json_jackson2_string_handwritten_fromBytes_serialized());
        validate("Jackson2 String recycler", json_jackson2_string_native(),
                json_jackson2_string_handwritten_recycler());

        byte[] jackson2Bytes = JACKSON2.writeValueAsBytes(USER);
        validate("Jackson2 serialized names", jackson2Bytes,
                json_jackson2_bytes_handwritten_serialized());
        validate("Jackson2 String names", jackson2Bytes,
                json_jackson2_bytes_handwritten());
        validate("Jackson2 stream", json_jackson2_stream_native(),
                json_jackson2_stream_handwritten());
        validate("Jackson2 stream serialized names", json_jackson2_stream_native(),
                json_jackson2_stream_handwritten_serialized());
        validate("Gson", GSON.toJson(USER), json_gson_handwritten());
        String fastjson2Native = JSON.toJSONString(USER, FASTJSON2_NATIVE_CONTEXT);
        validate("Fastjson2 string names", fastjson2Native,
                json_fastjson2_string_handwritten_utf8());
        validate("Fastjson2 serialized names", fastjson2Native,
                json_fastjson2_string_handwritten_utf8_serialized());
        validate("Fastjson2 UTF-8 packed raw names", fastjson2Native,
                json_fastjson2_string_handwritten_utf8_raw());
        validate("Fastjson2 UTF-16 string names", fastjson2Native,
                json_fastjson2_string_handwritten_utf16());
        validate("Fastjson2 UTF-16 serialized names", fastjson2Native,
                json_fastjson2_string_handwritten_utf16_serialized());
        validate("Fastjson2 UTF-16 raw names", fastjson2Native,
                json_fastjson2_string_handwritten_utf16_raw());
        byte[] fastjson2NativeBytes = json_fastjson2_bytes_native();
        validate("Fastjson2 UTF-8 bytes String names", fastjson2NativeBytes,
                json_fastjson2_bytes_handwritten());
        validate("Fastjson2 UTF-8 bytes serialized names", fastjson2NativeBytes,
                json_fastjson2_bytes_handwritten_serialized());
        validate("Fastjson2 UTF-8 bytes packed raw names", fastjson2NativeBytes,
                json_fastjson2_bytes_handwritten_raw());
        validate("Fastjson2 UTF-16 string to UTF-8 bytes String names", fastjson2NativeBytes,
                json_fastjson2_bytes_handwritten_fromUtf16String());

    }

    private static void validate(String backend, String nativeJson, String handwrittenJson) throws IOException {
        JsonNode nativeTree = JACKSON2.readTree(nativeJson);
        JsonNode handwrittenTree = JACKSON2.readTree(handwrittenJson);
        if (!nativeTree.equals(handwrittenTree)) {
            throw new IllegalStateException(backend + " output differs from reference: reference="
                    + nativeJson + ", candidate=" + handwrittenJson);
        }
    }

    private static void validate(String backend, byte[] nativeJson, byte[] handwrittenJson) throws IOException {
        JsonNode nativeTree = JACKSON2.readTree(nativeJson);
        JsonNode handwrittenTree = JACKSON2.readTree(handwrittenJson);
        if (!nativeTree.equals(handwrittenTree)) {
            throw new IllegalStateException(backend + " handwritten output differs from native output");
        }
    }

    // ----- Jackson2 baselines -----
    @Benchmark
    public String json_jackson2_string_native() throws IOException {
        return JACKSON2.writeValueAsString(USER);
    }

    @Benchmark
    public String json_jackson2_string_native_fromBytes() throws IOException {
        return new String(JACKSON2.writeValueAsBytes(USER), StandardCharsets.UTF_8);
    }

    @Benchmark
    public String json_jackson2_string_handwritten() throws IOException {
        StringWriter output = new StringWriter();
        JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output);
        Jackson2HandPojoStringedWriter.writeUser(generator, USER);
        generator.close();
        return output.toString();
    }

    @Benchmark
    public String json_jackson2_string_handwritten_serialized() throws IOException {
        StringWriter output = new StringWriter();
        JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output);
        Jackson2HandPojoSerializedWriter.writeUser(generator, USER);
        generator.close();
        return output.toString();
    }

    @Benchmark
    public String json_jackson2_string_handwritten_recycler() throws IOException {
        BufferRecycler recycler = JACKSON2_FACTORY._getBufferRecycler();
        try (SegmentedStringWriter output = new SegmentedStringWriter(recycler)) {
            JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output);
            Jackson2HandPojoStringedWriter.writeUser(generator, USER);
            generator.close();
            return output.getAndClear();
        } finally {
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public String json_jackson2_string_handwritten_fromBytes() throws IOException {
        BufferRecycler recycler = JACKSON2_FACTORY._getBufferRecycler();
        try (ByteArrayBuilder output = new ByteArrayBuilder(recycler)) {
            JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output, JsonEncoding.UTF8);
            Jackson2HandPojoStringedWriter.writeUser(generator, USER);
            generator.close();
            return new String(output.getClearAndRelease(), StandardCharsets.UTF_8);
        } finally {
            recycler.releaseToPool();
        }
    }

    @Benchmark
    public String json_jackson2_string_handwritten_fromBytes_serialized() throws IOException {
        BufferRecycler recycler = JACKSON2_FACTORY._getBufferRecycler();
        try (ByteArrayBuilder output = new ByteArrayBuilder(recycler)) {
            JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output, JsonEncoding.UTF8);
            Jackson2HandPojoSerializedWriter.writeUser(generator, USER);
            generator.close();
            return new String(output.getClearAndRelease(), StandardCharsets.UTF_8);
        } finally {
            recycler.releaseToPool();
        }
    }


    @Benchmark
    public byte[] json_jackson2_bytes_native() throws IOException {
        return JACKSON2.writeValueAsBytes(USER);
    }


    @Benchmark
    public byte[] json_jackson2_bytes_handwritten() throws IOException {
        try (ByteArrayBuilder output = new ByteArrayBuilder()) {
            JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output);
            Jackson2HandPojoStringedWriter.writeUser(generator, USER);
            generator.close();
            return output.toByteArray();
        }
    }

    @Benchmark
    public byte[] json_jackson2_bytes_handwritten_serialized() throws IOException {
        try (ByteArrayBuilder output = new ByteArrayBuilder()) {
            JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output);
            Jackson2HandPojoSerializedWriter.writeUser(generator, USER);
            generator.close();
            return output.toByteArray();
        }
    }


    @Benchmark
    public byte[] json_jackson2_stream_native() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JACKSON2.writeValue(output, USER);
        return output.toByteArray();
    }

    @Benchmark
    public byte[] json_jackson2_stream_handwritten() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output);
        Jackson2HandPojoStringedWriter.writeUser(generator, USER);
        generator.close();
        return output.toByteArray();
    }

    @Benchmark
    public byte[] json_jackson2_stream_handwritten_serialized() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JsonGenerator generator = JACKSON2_FACTORY.createGenerator(output);
        Jackson2HandPojoSerializedWriter.writeUser(generator, USER);
        generator.close();
        return output.toByteArray();
    }


    // ----- Gson baselines -----
    @Benchmark
    public String json_gson_native() {
        return GSON.toJson(USER);
    }

    @Benchmark
    public String json_gson_handwritten() throws IOException {
        StringWriter output = new StringWriter();
        JsonWriter writer = GSON.newJsonWriter(output);
        GsonHandPojoWriter.writeUser(writer, USER);
        return output.toString();
    }

    // ----- Fastjson2 baselines -----
    @Benchmark
    public String json_fastjson2_string_native() {
        return JSON.toJSONString(USER, FASTJSON2_NATIVE_CONTEXT);
    }

    @Benchmark
    public String json_fastjson2_string_handwritten_utf16() {
        try (JSONWriter writer = JSONWriter.ofUTF16()) {
            Fastjson2HandPojoStringedWriter.writeUser(writer, USER);
            return writer.toString();
        }
    }

    @Benchmark
    public String json_fastjson2_string_handwritten_utf16_serialized() {
        try (JSONWriter writer = JSONWriter.ofUTF16()) {
            Fastjson2HandPojoSerializedWriter.writeUser(writer, USER);
            return writer.toString();
        }
    }

    @Benchmark
    public String json_fastjson2_string_handwritten_utf16_raw() {
        try (JSONWriter writer = JSONWriter.ofUTF16()) {
            Fastjson2HandPojoRawWriter.writeUser(writer, USER);
            return writer.toString();
        }
    }

    @Benchmark
    public String json_fastjson2_string_handwritten_utf8() {
        try (JSONWriter writer = JSONWriter.ofUTF8(FASTJSON2_NATIVE_CONTEXT)) {
            Fastjson2HandPojoStringedWriter.writeUser(writer, USER);
            return writer.toString();
        }
    }

    @Benchmark
    public String json_fastjson2_string_handwritten_utf8_serialized() {
        try (JSONWriter writer = JSONWriter.ofUTF8(FASTJSON2_NATIVE_CONTEXT)) {
            Fastjson2HandPojoSerializedWriter.writeUser(writer, USER);
            return writer.toString();
        }
    }

    @Benchmark
    public String json_fastjson2_string_handwritten_utf8_raw() {
        try (JSONWriter writer = JSONWriter.ofUTF8(FASTJSON2_NATIVE_CONTEXT)) {
            Fastjson2HandPojoRawWriter.writeUser(writer, USER);
            return writer.toString();
        }
    }

    @Benchmark
    public byte[] json_fastjson2_bytes_native() {
        return JSON.toJSONBytes(USER, StandardCharsets.UTF_8, FASTJSON2_NATIVE_CONTEXT);
    }

    @Benchmark
    public byte[] json_fastjson2_bytes_handwritten() {
        try (JSONWriter writer = JSONWriter.ofUTF8(FASTJSON2_NATIVE_CONTEXT)) {
            Fastjson2HandPojoStringedWriter.writeUser(writer, USER);
            return writer.getBytes();
        }
    }

    @Benchmark
    public byte[] json_fastjson2_bytes_handwritten_fromUtf16String() {
        try (JSONWriter writer = JSONWriter.of(FASTJSON2_NATIVE_CONTEXT)) {
            Fastjson2HandPojoStringedWriter.writeUser(writer, USER);
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    @Benchmark
    public byte[] json_fastjson2_bytes_handwritten_serialized() {
        try (JSONWriter writer = JSONWriter.ofUTF8(FASTJSON2_NATIVE_CONTEXT)) {
            Fastjson2HandPojoSerializedWriter.writeUser(writer, USER);
            return writer.getBytes();
        }
    }

    @Benchmark
    public byte[] json_fastjson2_bytes_handwritten_raw() {
        try (JSONWriter writer = JSONWriter.ofUTF8(FASTJSON2_NATIVE_CONTEXT)) {
            Fastjson2HandPojoRawWriter.writeUser(writer, USER);
            return writer.getBytes();
        }
    }

}
