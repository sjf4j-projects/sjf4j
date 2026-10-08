package org.sjf4j.backend.jackson3.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.JsonParser;
import tools.jackson.core.json.JsonFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson3ReaderTest {

    @Test
    void preservesPeekAndConditionalStateAcrossObjectsAndArrays() throws Exception {
        try (Jackson3Reader reader = reader("{\"id\":7,\"name\":\"Ada\"}")) {
            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            assertFalse(reader.nextIfNull());
            assertTrue(reader.nextIfObjectStart());
            assertFalse(reader.nextIfArrayStart());
            assertEquals(StreamingReader.Token.NAME, reader.peekToken());
            assertEquals("id", reader.nextName());
            assertFalse(reader.nextIfNull());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(7, reader.readIntValue());
            assertFalse(reader.nextIfObjectEnd());
            assertEquals("name", reader.nextName());
            assertEquals("Ada", reader.readString());
            assertTrue(reader.nextIfObjectEnd());
            reader.endDocument();
        }

        try (Jackson3Reader reader = reader("[null]")) {
            assertTrue(reader.nextIfArrayStart());
            assertFalse(reader.nextIfArrayEnd());
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
            reader.endDocument();
        }
    }

    @Test
    void consumesTheCurrentTokenOfASuppliedParser() throws Exception {
        JsonParser parser = new JsonFactory().createParser(
                ObjectReadContext.empty(), "{\"id\":7}");
        parser.nextToken();

        try (Jackson3Reader reader = new Jackson3Binder(new JsonFactory()).createReader(parser)) {
            reader.startObject();
            assertEquals("id", reader.nextName());
            assertEquals(7, reader.readIntValue());
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void matchesCanonicalNamesNativelyAndAliasesThroughFallback() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson3NameMatcher(pojoInfo.writableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        try (Jackson3Reader reader = reader("{\"unknown\":[1,{\"nested\":true}],\"legacyName\":\"Ada\",\"id\":7}")) {
            reader.startObject();
            assertEquals(NameMatcher.UNKNOWN, reader.nextNameMatch(matcher));
            reader.skipNode();
            assertEquals(name, reader.nextNameMatch(matcher));
            assertEquals("Ada", reader.readString());
            assertEquals(id, reader.nextNameMatch(matcher));
            assertEquals(7, reader.readIntValue());
            assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
            reader.endDocument();
        }
    }

    @Test
    void readsScalarsWithCoreNumericAndCharacterSemantics() throws Exception {
        try (Jackson3Reader reader = reader("[1.9,128,1.25e2,123456789012345678901234567890,1.20e-3,\"x\",null,\"\",\"xy\"]")) {
            reader.startArray();
            assertEquals(1, reader.readIntValue());
            assertThrows(ArithmeticException.class, reader::readByteValue);
            assertEquals(125d, reader.readDoubleValue());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.readBigInteger());
            assertEquals(new BigDecimal("0.0012"), reader.readBigDecimal());
            assertEquals('x', reader.readCharValue());
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            reader.endArray();
        }
    }

    @Test
    void readsPrimitiveIntsAndLongsWithoutChangingNumericSemantics() throws Exception {
        try (Jackson3Reader reader = reader("[0,-1,1.9,2147483647,-2147483648,9223372036854775807]")) {
            reader.startArray();
            assertEquals(0, reader.readIntValue());
            assertEquals(-1L, reader.readLongValue());
            assertEquals(1, reader.readIntValue());
            assertEquals(Integer.MAX_VALUE, reader.readIntValue());
            assertEquals(Integer.MIN_VALUE, reader.readIntValue());
            assertEquals(Long.MAX_VALUE, reader.readLongValue());
            reader.endArray();
        }
    }

    @Test
    void readsRawNodesAndRejectsTrailingDocuments() throws Exception {
        try (Jackson3Reader reader = reader(
                "{\"text\":\"Ada\",\"number\":7,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}")) {
            Map<?, ?> value = (Map<?, ?>) reader.readRawNode();

            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals("Ada", value.get("text"));
            assertEquals(7, value.get("number"));
            assertEquals(true, value.get("enabled"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(ArrayList.class, value.get("items").getClass());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }

        try (Jackson3Reader reader = reader("null null")) {
            assertTrue(reader.nextIfNull());
            assertThrows(IOException.class, reader::endDocument);
        }
    }

    private static Jackson3Reader reader(String json) throws IOException {
        return new Jackson3Reader(new JsonFactory().createParser(ObjectReadContext.empty(), json));
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
