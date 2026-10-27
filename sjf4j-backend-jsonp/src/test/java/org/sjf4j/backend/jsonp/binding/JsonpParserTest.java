package org.sjf4j.backend.jsonp.binding;

import jakarta.json.Json;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.TypeRegistry;

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

class JsonpParserTest {

    @Test
    void exposesLogicalTokensAndConditionalConsumption() throws Exception {
        {
            JsonpParser parser = parser("{\"id\":null}");
            try {
                assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
                assertFalse(parser.nextIfArrayStart());
                parser.readObjectStart();
                assertEquals(StreamingParser.Token.NAME, parser.peekToken());
                assertEquals("id", parser.nextName());
                assertEquals(StreamingParser.Token.NULL, parser.peekToken());
                assertTrue(parser.nextIfNull());
                assertTrue(parser.nextIfObjectEnd());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void matchesNamesAndAliasesUsingTheCoreFallback() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new NameMatcher(pojoInfo.settableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        {
            JsonpParser parser = parser("{\"unknown\":[1,{\"nested\":true}],\"legacyName\":\"Ada\",\"id\":7}");
            try {
                parser.readObjectStart();
                assertEquals(NameMatcher.UNKNOWN, parser.nextNameMatch(matcher));
                parser.skipNode();
                assertEquals(name, parser.nextNameMatch(matcher));
                assertEquals("Ada", parser.readString());
                assertEquals(id, parser.nextNameMatch(matcher));
                assertEquals(7, parser.readIntValue());
                assertEquals(NameMatcher.OBJECT_END, parser.nextNameMatch(matcher));
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsScalarsWithCoreNumericAndCharacterSemantics() throws Exception {
        {
            JsonpParser parser = parser("[1.9,128,1.25e2,123456789012345678901234567890,1.20e-3,\"x\",null,\"\",\"xy\"]");
            try {
                parser.readArrayStart();
                assertEquals(1, parser.readIntValue());
                assertThrows(ArithmeticException.class, parser::readByteValue);
                assertEquals(125d, parser.readDoubleValue());
                assertEquals(new BigInteger("123456789012345678901234567890"), parser.readBigInteger());
                assertEquals(new BigDecimal("0.00120"), parser.readBigDecimal());
                assertEquals('x', parser.readCharValue());
                assertThrows(BindingException.class, parser::readCharValue);
                assertThrows(BindingException.class, parser::readCharValue);
                assertThrows(BindingException.class, parser::readCharValue);
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsIntShortAndByteThroughNativeIntegerConversions() throws Exception {
        {
            JsonpParser parser = parser("[2147483647,-2147483648,32767,-32768,127,-128,1.9]");
            try {
                parser.readArrayStart();
                assertEquals(Integer.MAX_VALUE, parser.readIntValue());
                assertEquals(Integer.MIN_VALUE, parser.readIntValue());
                assertEquals(Short.MAX_VALUE, parser.readShortValue());
                assertEquals(Short.MIN_VALUE, parser.readShortValue());
                assertEquals(Byte.MAX_VALUE, parser.readByteValue());
                assertEquals(Byte.MIN_VALUE, parser.readByteValue());
                assertEquals(1, parser.readIntValue());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void detectsShortAndByteOverflowAfterNativeIntRead() throws Exception {
        {
            JsonpParser parser = parser("[32768,-32769,128,-129,7]");
            try {
                parser.readArrayStart();
                assertThrows(ArithmeticException.class, parser::readShortValue);
                assertThrows(ArithmeticException.class, parser::readShortValue);
                assertThrows(ArithmeticException.class, parser::readByteValue);
                assertThrows(ArithmeticException.class, parser::readByteValue);
                assertEquals(7, parser.readIntValue());
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void preservesRawNodeAndNumberSemantics() throws Exception {
        {
            JsonpParser parser = parser(
                "{\"text\":\"Ada\",\"number\":7,\"large\":123456789012345678901234567890,"
                        + "\"decimal\":1e10000,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}");
            try {

                Map<?, ?> value = (Map<?, ?>) parser.readRaw();

                assertEquals(LinkedHashMap.class, value.getClass());
                assertEquals(Arrays.asList("text", "number", "large", "decimal", "enabled", "empty", "nested", "items"),
                        Arrays.asList(value.keySet().toArray()));
                assertEquals(7, value.get("number"));
                assertEquals(new BigInteger("123456789012345678901234567890"), value.get("large"));
                assertEquals(new BigDecimal("1e10000"), value.get("decimal"));
                assertNull(value.get("empty"));
                assertEquals(LinkedHashMap.class, value.get("nested").getClass());
                assertEquals(ArrayList.class, value.get("items").getClass());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void skipsStructuresAndRejectsTrailingDocuments() throws Exception {
        {
            JsonpParser parser = parser("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]");
            try {
                parser.readArrayStart();
                parser.skipNode();
                assertEquals("kept", parser.readString());
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }

        {
            JsonpParser parser = parser("null null");
            try {
                assertThrows(Exception.class, parser::nextIfNull);

            } finally {
                parser.close();
            }
        }

    }

    @Test
    void retainsTheCurrentEventOfASuppliedParser() throws Exception {
        JsonParser nativeParser = Json.createParser(new StringReader("[1,2]"));
        nativeParser.next();
        nativeParser.next();

        {
            JsonpParser parser = new JsonpParser(nativeParser);
            try {
                assertEquals(1, parser.readIntValue());
                assertEquals(2, parser.readIntValue());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void closesTheSuppliedParser() throws Exception {
        JsonParser nativeParser = Json.createParser(new StringReader("null"));
        AtomicBoolean closed = new AtomicBoolean();
        JsonParser tracked = (JsonParser) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { JsonParser.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) {
                        closed.set(true);
                    }
                    return method.invoke(nativeParser, arguments);
                });
        JsonpParser parser = new JsonpParser(tracked);
        parser.close();
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
        {
            JsonpParser parser = new JsonpParser(truncated);
            try {
                IOException error = assertThrows(IOException.class, parser::skipNode);
                assertTrue(error.getMessage().contains("composite end"));

            } finally {
                parser.close();
            }
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
        {
            JsonpParser parser = new JsonpParser(legacy);
            try {
                parser.readArrayStart();
                assertEquals(7, parser.readIntValue());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsBoundaryIntegerNumbers() throws Exception {
        {
            JsonpParser parser = parser("[9223372036854775807,-9223372036854775808,9223372036854775808]");
            try {
                parser.readArrayStart();
                assertEquals(Long.MAX_VALUE, parser.readLongValue());
                assertEquals(Long.MIN_VALUE, parser.readLongValue());
                assertThrows(ArithmeticException.class, parser::readLongValue);
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }
    }

    private static JsonpParser parser(String json) {
        return new JsonpParser(Json.createParser(new StringReader(json)));
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
