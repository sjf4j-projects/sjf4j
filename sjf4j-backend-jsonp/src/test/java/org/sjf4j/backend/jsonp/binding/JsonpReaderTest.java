package org.sjf4j.backend.jsonp.binding;

import jakarta.json.Json;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonpReaderTest {

    @Test
    void exposesLogicalTokensAndConditionalConsumption() throws Exception {
        try (JsonpReader reader = reader("{\"id\":null}")) {
            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            assertFalse(reader.nextIfArrayStart());
            reader.startObject();
            assertEquals(StreamingReader.Token.NAME, reader.peekToken());
            assertEquals("id", reader.nextName());
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfObjectEnd());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void matchesNamesAndAliasesUsingTheCoreFallback() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new NameMatcher(pojoInfo.writableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        try (JsonpReader reader = reader("{\"unknown\":[1,{\"nested\":true}],\"legacyName\":\"Ada\",\"id\":7}")) {
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
        try (JsonpReader reader = reader("[1.9,128,1.25e2,123456789012345678901234567890,1.20e-3,\"x\",null,\"\",\"xy\"]")) {
            reader.startArray();
            assertEquals(1, reader.readIntValue());
            assertThrows(ArithmeticException.class, reader::readByteValue);
            assertEquals(125d, reader.readDoubleValue());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.readBigInteger());
            assertEquals(new BigDecimal("0.00120"), reader.readBigDecimal());
            assertEquals('x', reader.readCharValue());
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            assertThrows(BindingException.class, reader::readCharValue);
            reader.endArray();
        }
    }

    @Test
    void readsIntShortAndByteThroughNativeIntegerConversions() throws Exception {
        try (JsonpReader reader = reader("[2147483647,-2147483648,32767,-32768,127,-128,1.9]")) {
            reader.startArray();
            assertEquals(Integer.MAX_VALUE, reader.readIntValue());
            assertEquals(Integer.MIN_VALUE, reader.readIntValue());
            assertEquals(Short.MAX_VALUE, reader.readShortValue());
            assertEquals(Short.MIN_VALUE, reader.readShortValue());
            assertEquals(Byte.MAX_VALUE, reader.readByteValue());
            assertEquals(Byte.MIN_VALUE, reader.readByteValue());
            assertEquals(1, reader.readIntValue());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void detectsShortAndByteOverflowAfterNativeIntRead() throws Exception {
        try (JsonpReader reader = reader("[32768,-32769,128,-129,7]")) {
            reader.startArray();
            assertThrows(ArithmeticException.class, reader::readShortValue);
            assertThrows(ArithmeticException.class, reader::readShortValue);
            assertThrows(ArithmeticException.class, reader::readByteValue);
            assertThrows(ArithmeticException.class, reader::readByteValue);
            assertEquals(7, reader.readIntValue());
            reader.endArray();
        }
    }

    @Test
    void preservesRawNodeAndNumberSemantics() throws Exception {
        try (JsonpReader reader = reader(
                "{\"text\":\"Ada\",\"number\":7,\"large\":123456789012345678901234567890,"
                        + "\"decimal\":1e10000,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}")) {

            Map<?, ?> value = (Map<?, ?>) reader.readRawNode();

            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "large", "decimal", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals(7, value.get("number"));
            assertEquals(new BigInteger("123456789012345678901234567890"), value.get("large"));
            assertEquals(new BigDecimal("1e10000"), value.get("decimal"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(ArrayList.class, value.get("items").getClass());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void skipsStructuresAndRejectsTrailingDocuments() throws Exception {
        try (JsonpReader reader = reader("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]")) {
            reader.startArray();
            reader.skipNode();
            assertEquals("kept", reader.readString());
            reader.endArray();
        }

        try (JsonpReader reader = reader("null null")) {
            assertThrows(Exception.class, reader::nextIfNull);
        }

    }

    @Test
    void retainsTheCurrentEventOfASuppliedParser() throws Exception {
        JsonParser parser = Json.createParser(new StringReader("[1,2]"));
        parser.next();
        parser.next();

        try (JsonpReader reader = new JsonpReader(parser)) {
            assertEquals(1, reader.readIntValue());
            assertEquals(2, reader.readIntValue());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void closesTheSuppliedParser() throws Exception {
        JsonParser parser = Json.createParser(new StringReader("null"));
        AtomicBoolean closed = new AtomicBoolean();
        JsonParser tracked = (JsonParser) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { JsonParser.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) {
                        closed.set(true);
                    }
                    return method.invoke(parser, arguments);
                });
        JsonpReader reader = new JsonpReader(tracked);
        reader.close();
        assertTrue(closed.get());
    }

    @Test
    void skipCompositeRejectsPrematureEofFromCustomParser() throws Exception {
        JsonParser delegate = Json.createParser(new StringReader("[1,2]"));
        JsonParser truncated = (JsonParser) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{JsonParser.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasNext")) {
                        return false;
                    }
                    if (method.getName().equals("currentEvent")) {
                        return JsonParser.Event.START_ARRAY;
                    }
                    return method.invoke(delegate, args);
                });
        try (JsonpReader reader = new JsonpReader(truncated)) {
            IOException error = assertThrows(IOException.class, reader::skipNode);
            assertTrue(error.getMessage().contains("composite end"));
        }
    }

    @Test
    void startsAtFirstEventWhenCurrentEventIsUnsupported() throws Exception {
        JsonParser delegate = Json.createParser(new StringReader("[7]"));
        JsonParser legacy = (JsonParser) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{JsonParser.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("currentEvent")) {
                        throw new UnsupportedOperationException();
                    }
                    return method.invoke(delegate, args);
                });
        try (JsonpReader reader = new JsonpReader(legacy)) {
            reader.startArray();
            assertEquals(7, reader.readIntValue());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void readsBoundaryIntegerNumbers() throws Exception {
        try (JsonpReader reader = reader("[9223372036854775807,-9223372036854775808,9223372036854775808]")) {
            reader.startArray();
            assertEquals(Long.MAX_VALUE, reader.readLongValue());
            assertEquals(Long.MIN_VALUE, reader.readLongValue());
            assertThrows(ArithmeticException.class, reader::readLongValue);
            reader.endArray();
        }
    }

    private static JsonpReader reader(String json) {
        return new JsonpReader(Json.createParser(new StringReader(json)));
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
