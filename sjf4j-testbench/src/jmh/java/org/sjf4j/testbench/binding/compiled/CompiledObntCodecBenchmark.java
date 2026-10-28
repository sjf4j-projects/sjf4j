package org.sjf4j.testbench.binding.compiled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.binding.WriteTo;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.binding.Binder;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Standalone benchmark for compiled JOJO, JSON wrappers and ValueCodec binding.
 *
 * Every invocation parses or serializes a complete document. The input fixture
 * and source model are identical for Runtime and Compiled. Each trial validates
 * semantic equivalence BEFORE measuring. Selection via method references adds
 * a small, symmetric dispatch cost to Runtime and Compiled measurements.
 *
 * Usage:
 *   ./gradlew :sjf4j-testbench:jmhJar
 *   java -jar sjf4j-testbench/build/libs/*-jmh.jar \
 *     '.*CompiledObntCodecBenchmark.*' -p backend=fastjson2 -prof gc
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class CompiledObntCodecBenchmark {

    public static void main(String[] args) throws Exception {
        Main.main(new String[]{CompiledObntCodecBenchmark.class.getName()});
    }

    private static final String CODEC_JSON =
            "{\"id\":11,\"when\":1700000000123,\"day\":\"2026/04/05\","
                    + "\"token\":\"id-11\",\"tokens\":[\"a\",\"b\"]}";
    private static final String JOJO_JSON =
            "{\"id\":11,\"title\":\"open\",\"payload\":{\"x\":1},"
                    + "\"items\":[true,null],\"extra\":{\"nested\":[1,2]},\"optional\":null}";
    private static final String OBJECT_JSON =
            "{\"name\":\"payload\",\"count\":7,\"nested\":{\"a\":[1,null,2]}}";
    private static final String ARRAY_JSON =
            "[1,\"second\",null,{\"enabled\":true}]";

    private static final ObjectMapper REFERENCE = new ObjectMapper();

    @Param({"fastjson2", "jackson2", "jackson3", "gson", "jsonp", "simple"})
    public String backend;

    private Binder<?, ?> runtime;
    private Read<CodecPojo> readCodec;
    private Write<CodecPojo> writeCodec;
    private Read<DynamicJojo> readJojo;
    private Write<DynamicJojo> writeJojo;
    private Read<JsonObject> readObject;
    private Write<JsonObject> writeObject;
    private Read<JsonArray> readArray;
    private Write<JsonArray> writeArray;

    private CodecPojo codecValue;
    private DynamicJojo jojoValue;
    private JsonObject objectValue;
    private JsonArray arrayValue;

    @FunctionalInterface
    private interface Read<T> {
        T apply(String json) throws IOException;
    }

    @FunctionalInterface
    private interface Write<T> {
        String apply(T value) throws IOException;
    }

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        switch (backend) {
            case "fastjson2": {
                Fastjson2Compiled compiled = CompiledInstances.create(Fastjson2Compiled.class);
                runtime = new org.sjf4j.backend.fastjson2.binding.Fastjson2Binder();
                readCodec = compiled::readCodec;
                writeCodec = compiled::writeCodec;
                readJojo = compiled::readJojo;
                writeJojo = compiled::writeJojo;
                readObject = compiled::readObject;
                writeObject = compiled::writeObject;
                readArray = compiled::readArray;
                writeArray = compiled::writeArray;
                break;
            }
            case "jackson2": {
                Jackson2Compiled compiled = CompiledInstances.create(Jackson2Compiled.class);
                runtime = new org.sjf4j.backend.jackson2.binding.Jackson2Binder();
                readCodec = compiled::readCodec;
                writeCodec = compiled::writeCodec;
                readJojo = compiled::readJojo;
                writeJojo = compiled::writeJojo;
                readObject = compiled::readObject;
                writeObject = compiled::writeObject;
                readArray = compiled::readArray;
                writeArray = compiled::writeArray;
                break;
            }
            case "jackson3": {
                Jackson3Compiled compiled = CompiledInstances.create(Jackson3Compiled.class);
                runtime = new org.sjf4j.backend.jackson3.binding.Jackson3Binder();
                readCodec = compiled::readCodec;
                writeCodec = compiled::writeCodec;
                readJojo = compiled::readJojo;
                writeJojo = compiled::writeJojo;
                readObject = compiled::readObject;
                writeObject = compiled::writeObject;
                readArray = compiled::readArray;
                writeArray = compiled::writeArray;
                break;
            }
            case "gson": {
                GsonCompiled compiled = CompiledInstances.create(GsonCompiled.class);
                runtime = new org.sjf4j.backend.gson.binding.GsonBinder();
                readCodec = compiled::readCodec;
                writeCodec = compiled::writeCodec;
                readJojo = compiled::readJojo;
                writeJojo = compiled::writeJojo;
                readObject = compiled::readObject;
                writeObject = compiled::writeObject;
                readArray = compiled::readArray;
                writeArray = compiled::writeArray;
                break;
            }
            case "jsonp": {
                JsonpCompiled compiled = CompiledInstances.create(JsonpCompiled.class);
                runtime = new org.sjf4j.backend.jsonp.binding.JsonpBinder();
                readCodec = compiled::readCodec;
                writeCodec = compiled::writeCodec;
                readJojo = compiled::readJojo;
                writeJojo = compiled::writeJojo;
                readObject = compiled::readObject;
                writeObject = compiled::writeObject;
                readArray = compiled::readArray;
                writeArray = compiled::writeArray;
                break;
            }
            case "simple": {
                SimpleCompiled compiled = CompiledInstances.create(SimpleCompiled.class);
                runtime = new org.sjf4j.binding.simple.SimpleJsonBinder();
                readCodec = compiled::readCodec;
                writeCodec = compiled::writeCodec;
                readJojo = compiled::readJojo;
                writeJojo = compiled::writeJojo;
                readObject = compiled::readObject;
                writeObject = compiled::writeObject;
                readArray = compiled::readArray;
                writeArray = compiled::writeArray;
                break;
            }
            default:
                throw new IllegalArgumentException(backend);
        }

        codecValue = readCodec.apply(CODEC_JSON);
        jojoValue = readJojo.apply(JOJO_JSON);
        objectValue = readObject.apply(OBJECT_JSON);
        arrayValue = readArray.apply(ARRAY_JSON);

        // Verify read results and full-document writes, outside the measured path.
        if (codecValue.id != 11 || codecValue.when.toEpochMilli() != 1700000000123L
                || !LocalDate.of(2026, 4, 5).equals(codecValue.day)
                || !"id-11".equals(codecValue.token.value)
                || codecValue.tokens.size() != 2
                || !"b".equals(codecValue.tokens.get(1).value)) {
            throw new IllegalStateException(backend + ": incorrect compiled ValueCodec read");
        }
        if (jojoValue.id != 11 || !"open".equals(jojoValue.title)
                || !jojoValue.dynamicMembers().containsKey("extra")
                || !jojoValue.dynamicMembers().containsKey("optional")
                || jojoValue.payload == null || jojoValue.items == null) {
            throw new IllegalStateException(backend + ": incorrect compiled JOJO read");
        }
        if (objectValue.size() != 3 || arrayValue.size() != 4) {
            throw new IllegalStateException(backend + ": incorrect compiled JSON wrappers");
        }
        verify("codec compiled", CODEC_JSON, writeCodec.apply(codecValue));
        verify("codec runtime", CODEC_JSON, runtime.writeNodeAsString(
                runtime.readNode(CODEC_JSON, CodecPojo.class)));
        verify("jojo compiled", JOJO_JSON, writeJojo.apply(jojoValue));
        verify("jojo runtime", JOJO_JSON, runtime.writeNodeAsString(
                runtime.readNode(JOJO_JSON, DynamicJojo.class)));
        verify("object compiled", OBJECT_JSON, writeObject.apply(objectValue));
        verify("object runtime", OBJECT_JSON, runtime.writeNodeAsString(
                runtime.readNode(OBJECT_JSON, JsonObject.class)));
        verify("array compiled", ARRAY_JSON, writeArray.apply(arrayValue));
        verify("array runtime", ARRAY_JSON, runtime.writeNodeAsString(
                runtime.readNode(ARRAY_JSON, JsonArray.class)));
    }

    private static void verify(String label, String expected, String actual) throws IOException {
        JsonNode reference = REFERENCE.readTree(expected);
        JsonNode result = REFERENCE.readTree(actual);
        if (!reference.equals(result)) {
            throw new IllegalStateException(label + ": expected " + reference + " but got " + result);
        }
    }

    // Codec: annotated value, named Instant codec, patterned LocalDate codec,
    // and value codecs inside a typed List.
    @Benchmark public CodecPojo codec_read_compiled() throws IOException {
        return readCodec.apply(CODEC_JSON);
    }
    @Benchmark public Object codec_read_runtime() {
        return runtime.readNode(CODEC_JSON, CodecPojo.class);
    }
    @Benchmark public String codec_write_compiled() throws IOException {
        return writeCodec.apply(codecValue);
    }
    @Benchmark public String codec_write_runtime() {
        return runtime.writeNodeAsString(codecValue);
    }

    // JOJO: declared fields plus unknown object and JSON null.
    @Benchmark public DynamicJojo jojo_read_compiled() throws IOException {
        return readJojo.apply(JOJO_JSON);
    }
    @Benchmark public Object jojo_read_runtime() {
        return runtime.readNode(JOJO_JSON, DynamicJojo.class);
    }
    @Benchmark public String jojo_write_compiled() throws IOException {
        return writeJojo.apply(jojoValue);
    }
    @Benchmark public String jojo_write_runtime() {
        return runtime.writeNodeAsString(jojoValue);
    }

    // Unstructured wrappers: no declared POJO field to optimize.
    @Benchmark public JsonObject object_read_compiled() throws IOException {
        return readObject.apply(OBJECT_JSON);
    }
    @Benchmark public Object object_read_runtime() {
        return runtime.readNode(OBJECT_JSON, JsonObject.class);
    }
    @Benchmark public String object_write_compiled() throws IOException {
        return writeObject.apply(objectValue);
    }
    @Benchmark public String object_write_runtime() {
        return runtime.writeNodeAsString(objectValue);
    }
    @Benchmark public JsonArray array_read_compiled() throws IOException {
        return readArray.apply(ARRAY_JSON);
    }
    @Benchmark public Object array_read_runtime() {
        return runtime.readNode(ARRAY_JSON, JsonArray.class);
    }
    @Benchmark public String array_write_compiled() throws IOException {
        return writeArray.apply(arrayValue);
    }
    @Benchmark public String array_write_runtime() {
        return runtime.writeNodeAsString(arrayValue);
    }

    @NodeValue
    public static final class Token {
        public final String value;

        public Token(String value) {
            this.value = value;
        }

        @ValueToRaw public String toRaw() {
            return value;
        }

        @RawToValue public static Token fromRaw(String raw) {
            return new Token(raw == null ? "NULL_INPUT" : raw);
        }
    }

    public static class CodecPojo {
        public long id;
        @NodeProperty(codecName = "epochMillis") public Instant when;
        @NodeProperty(codecPattern = "yyyy/MM/dd") public LocalDate day;
        public Token token;
        public List<Token> tokens;
    }

    public static class DynamicJojo extends JsonObject {
        public long id;
        public String title;
        public JsonObject payload;
        public JsonArray items;
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2Compiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom DynamicJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(DynamicJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Compiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom DynamicJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(DynamicJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Compiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom DynamicJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(DynamicJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom DynamicJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(DynamicJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCompiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom DynamicJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(DynamicJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCompiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom DynamicJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(DynamicJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
    }
}
