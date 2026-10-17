package org.sjf4j.testbench.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent integration checks for the P1/P2 compiled binding features.
 * Exercises actual generated binder implementations (not a generated-source mock).
 *
 * Local: ./gradlew :sjf4j-testbench:test --tests '*CompiledObntCodecTest'
 */
class CompiledObntCodecTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CODEC_JSON =
            "{\"id\":17,\"when\":1234,\"date\":\"2026/10/17\","
                    + "\"token\":null,\"tokens\":[\"a\",\"b\"]}";
    private static final String JOJO_JSON =
            "{\"unknown\":{\"nested\":[1,2]},\"oldName\":\"Alice\","
                    + "\"id\":3,\"payload\":{\"n\":5},\"items\":[true,null],"
                    + "\"readOnly\":\"discard\",\"note\":null}";

    @FunctionalInterface
    interface Reader<T> {
        T read(String json) throws IOException;
    }

    @FunctionalInterface
    interface Writer<T> {
        String write(T value) throws IOException;
    }

    @Test
    void allJsonBackendsMatchRuntimeAndRespectCodecAndJojoContracts() throws Exception {
        Fastjson2Compiled fastjson2 = CompiledInstances.of(Fastjson2Compiled.class);
        check("Fastjson2", new org.sjf4j.backend.fastjson2.binding.Fastjson2Binder(),
                fastjson2::readCodec, fastjson2::writeCodec,
                fastjson2::readJojo, fastjson2::writeJojo,
                fastjson2::readObject, fastjson2::writeObject,
                fastjson2::readArray, fastjson2::writeArray,
                fastjson2::readToken, fastjson2::writeToken);
        Jackson2Compiled jackson2 = CompiledInstances.of(Jackson2Compiled.class);
        check("Jackson2", new org.sjf4j.backend.jackson2.binding.Jackson2Binder(),
                jackson2::readCodec, jackson2::writeCodec,
                jackson2::readJojo, jackson2::writeJojo,
                jackson2::readObject, jackson2::writeObject,
                jackson2::readArray, jackson2::writeArray,
                jackson2::readToken, jackson2::writeToken);
        Jackson3Compiled jackson3 = CompiledInstances.of(Jackson3Compiled.class);
        check("Jackson3", new org.sjf4j.backend.jackson3.binding.Jackson3Binder(),
                jackson3::readCodec, jackson3::writeCodec,
                jackson3::readJojo, jackson3::writeJojo,
                jackson3::readObject, jackson3::writeObject,
                jackson3::readArray, jackson3::writeArray,
                jackson3::readToken, jackson3::writeToken);
        GsonCompiled gson = CompiledInstances.of(GsonCompiled.class);
        check("Gson", new org.sjf4j.backend.gson.binding.GsonBinder(),
                gson::readCodec, gson::writeCodec,
                gson::readJojo, gson::writeJojo,
                gson::readObject, gson::writeObject,
                gson::readArray, gson::writeArray,
                gson::readToken, gson::writeToken);
        JsonpCompiled jsonp = CompiledInstances.of(JsonpCompiled.class);
        check("Jsonp", new org.sjf4j.backend.jsonp.binding.JsonpBinder(),
                jsonp::readCodec, jsonp::writeCodec,
                jsonp::readJojo, jsonp::writeJojo,
                jsonp::readObject, jsonp::writeObject,
                jsonp::readArray, jsonp::writeArray,
                jsonp::readToken, jsonp::writeToken);
        SimpleCompiled simple = CompiledInstances.of(SimpleCompiled.class);
        check("Simple", new org.sjf4j.binding.simple.SimpleJsonBinder(),
                simple::readCodec, simple::writeCodec,
                simple::readJojo, simple::writeJojo,
                simple::readObject, simple::writeObject,
                simple::readArray, simple::writeArray,
                simple::readToken, simple::writeToken);
    }

    private static void check(String name, Binder<?, ?> runtime,
                              Reader<CodecPojo> readCodec, Writer<CodecPojo> writeCodec,
                              Reader<OpenJojo> readJojo, Writer<OpenJojo> writeJojo,
                              Reader<JsonObject> readObject, Writer<JsonObject> writeObject,
                              Reader<JsonArray> readArray, Writer<JsonArray> writeArray,
                              Reader<Token> readToken, Writer<Token> writeToken) throws Exception {

        CodecPojo codec = readCodec.read(CODEC_JSON);
        assertEquals(17L, codec.id, name);
        assertEquals(Instant.ofEpochMilli(1234), codec.when, name);
        assertEquals(LocalDate.of(2026, 10, 17), codec.date, name);
        assertNotNull(codec.token, name + ": rawToValue must receive JSON null");
        assertEquals("NULL_INPUT", codec.token.value, name);
        assertEquals("a", codec.tokens.get(0).value, name);
        assertEquals("b", codec.tokens.get(1).value, name);

        // Round-trip after nullable codec decoding: null becomes NULL_INPUT.
        String actual = writeCodec.write(codec);
        JsonNode tree = JSON.readTree(actual);
        assertEquals("NULL_INPUT", tree.path("token").asText(), name);
        assertEquals(1234L, tree.path("when").asLong(), name);
        assertEquals("2026/10/17", tree.path("date").asText(), name);
        assertJsonEquals(name + " codec Runtime", runtime.writeNodeAsString(codec), actual);

        assertEquals("NULL_INPUT", readToken.read("null").value, name);
        assertEquals("\"NULL_INPUT\"", writeToken.write(readToken.read("null")), name);
        assertEquals("null", writeToken.write(null), name);

        OpenJojo jojo = readJojo.read(JOJO_JSON);
        assertEquals(3L, jojo.id, name);
        assertEquals("Alice", jojo.name, name);
        assertEquals(1, jojo.payload.size(), name);
        assertEquals(2, jojo.items.size(), name);
        assertTrue(jojo.dynamicProperties().containsKey("unknown"), name);
        assertTrue(jojo.dynamicProperties().containsKey("note"), name);
        assertFalse(jojo.dynamicProperties().containsKey("oldName"), name);
        assertFalse(jojo.dynamicProperties().containsKey("readOnly"), name);
        String jojoOutput = writeJojo.write(jojo);
        JsonNode jojoTree = JSON.readTree(jojoOutput);
        assertEquals("computed", jojoTree.path("readOnly").asText(), name);
        assertEquals(2, jojoTree.path("unknown").path("nested").size(), name);
        assertTrue(jojoTree.path("note").isNull(), name);
        assertJsonEquals(name + " JOJO Runtime", runtime.writeNodeAsString(jojo), jojoOutput);

        JsonObject object = readObject.read("{\"value\":1,\"child\":{\"active\":true}}");
        assertEquals(2, object.size(), name);
        assertJsonEquals(name + " JsonObject", runtime.writeNodeAsString(object),
                writeObject.write(object));

        JsonArray array = readArray.read("[1,{\"x\":2},null]");
        assertEquals(3, array.size(), name);
        assertJsonEquals(name + " JsonArray", runtime.writeNodeAsString(array),
                writeArray.write(array));
    }

    private static void assertJsonEquals(String name, String expected, String actual)
            throws IOException {
        assertEquals(JSON.readTree(expected), JSON.readTree(actual), name);
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
        @NodeProperty(codecPattern = "yyyy/MM/dd") public LocalDate date;
        public Token token;
        public List<Token> tokens;
    }

    public static class OpenJojo extends JsonObject {
        public long id;
        @NodeProperty(aliases = {"oldName"}) public String name;
        public JsonObject payload;
        public JsonArray items;
        public String getReadOnly() {
            return "computed";
        }
    }

    @CompiledBinder(backend = Backend.FASTJSON2)
    public interface Fastjson2Compiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom OpenJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(OpenJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
        @ReadFrom Token readToken(String json) throws IOException;
        @WriteTo String writeToken(Token value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    public interface Jackson2Compiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom OpenJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(OpenJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
        @ReadFrom Token readToken(String json) throws IOException;
        @WriteTo String writeToken(Token value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JACKSON3)
    public interface Jackson3Compiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom OpenJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(OpenJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
        @ReadFrom Token readToken(String json) throws IOException;
        @WriteTo String writeToken(Token value) throws IOException;
    }

    @CompiledBinder(backend = Backend.GSON)
    public interface GsonCompiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom OpenJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(OpenJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
        @ReadFrom Token readToken(String json) throws IOException;
        @WriteTo String writeToken(Token value) throws IOException;
    }

    @CompiledBinder(backend = Backend.JSONP)
    public interface JsonpCompiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom OpenJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(OpenJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
        @ReadFrom Token readToken(String json) throws IOException;
        @WriteTo String writeToken(Token value) throws IOException;
    }

    @CompiledBinder(backend = Backend.SIMPLE)
    public interface SimpleCompiled {
        @ReadFrom CodecPojo readCodec(String json) throws IOException;
        @WriteTo String writeCodec(CodecPojo value) throws IOException;
        @ReadFrom OpenJojo readJojo(String json) throws IOException;
        @WriteTo String writeJojo(OpenJojo value) throws IOException;
        @ReadFrom JsonObject readObject(String json) throws IOException;
        @WriteTo String writeObject(JsonObject value) throws IOException;
        @ReadFrom JsonArray readArray(String json) throws IOException;
        @WriteTo String writeArray(JsonArray value) throws IOException;
        @ReadFrom Token readToken(String json) throws IOException;
        @WriteTo String writeToken(Token value) throws IOException;
    }
}
