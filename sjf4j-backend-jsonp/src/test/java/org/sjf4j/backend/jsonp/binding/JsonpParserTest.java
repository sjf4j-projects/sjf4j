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
JsonpParser reader = reader("{\"id\":null}");
try {
            assertEquals(StreamingParser.Token.OBJECT_START, reader.peekToken());
            assertFalse(reader.nextIfArrayStart());
            reader.startObject();
            assertEquals(StreamingParser.Token.NAME, reader.peekToken());
            assertEquals("id", reader.nextName());
            assertEquals(StreamingParser.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfObjectEnd());
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void matchesNamesAndAliasesUsingTheCoreFallback() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new NameMatcher(pojoInfo.writableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        {
JsonpParser reader = reader("{\"unknown\":[1,{\"nested\":true}],\"legacyName\":\"Ada\",\"id\":7}");
try {
            reader.startObject();
            assertEquals(NameMatcher.UNKNOWN, reader.nextNameMatch(matcher));
            reader.skipNode();
            assertEquals(name, reader.nextNameMatch(matcher));
            assertEquals("Ada", reader.readString());
            assertEquals(id, reader.nextNameMatch(matcher));
            assertEquals(7, reader.readIntValue());
            assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void readsScalarsWithCoreNumericAndCharacterSemantics() throws Exception {
        {
JsonpParser reader = reader("[1.9,128,1.25e2,123456789012345678901234567890,1.20e-3,\"x\",null,\"\",\"xy\"]");
try {
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
        
} finally { reader.release(); }
}
    }

    @Test
    void readsIntShortAndByteThroughNativeIntegerConversions() throws Exception {
        {
JsonpParser reader = reader("[2147483647,-2147483648,32767,-32768,127,-128,1.9]");
try {
            reader.startArray();
            assertEquals(Integer.MAX_VALUE, reader.readIntValue());
            assertEquals(Integer.MIN_VALUE, reader.readIntValue());
            assertEquals(Short.MAX_VALUE, reader.readShortValue());
            assertEquals(Short.MIN_VALUE, reader.readShortValue());
            assertEquals(Byte.MAX_VALUE, reader.readByteValue());
            assertEquals(Byte.MIN_VALUE, reader.readByteValue());
            assertEquals(1, reader.readIntValue());
            reader.endArray();
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void detectsShortAndByteOverflowAfterNativeIntRead() throws Exception {
        {
JsonpParser reader = reader("[32768,-32769,128,-129,7]");
try {
            reader.startArray();
            assertThrows(ArithmeticException.class, reader::readShortValue);
            assertThrows(ArithmeticException.class, reader::readShortValue);
            assertThrows(ArithmeticException.class, reader::readByteValue);
            assertThrows(ArithmeticException.class, reader::readByteValue);
            assertEquals(7, reader.readIntValue());
            reader.endArray();
        
} finally { reader.release(); }
}
    }

    @Test
    void preservesRawNodeAndNumberSemantics() throws Exception {
        {
JsonpParser reader = reader(
                "{\"text\":\"Ada\",\"number\":7,\"large\":123456789012345678901234567890,"
                        + "\"decimal\":1e10000,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}");
try {

            Map<?, ?> value = (Map<?, ?>) reader.readRaw();

            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "large", "decimal", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals(7, value.get("number"));
            assertEquals(new BigInteger("123456789012345678901234567890"), value.get("large"));
            assertEquals(new BigDecimal("1e10000"), value.get("decimal"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(ArrayList.class, value.get("items").getClass());
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void skipsStructuresAndRejectsTrailingDocuments() throws Exception {
        {
JsonpParser reader = reader("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]");
try {
            reader.startArray();
            reader.skipNode();
            assertEquals("kept", reader.readString());
            reader.endArray();
        
} finally { reader.release(); }
}

        {
JsonpParser reader = reader("null null");
try {
            assertThrows(Exception.class, reader::nextIfNull);
        
} finally { reader.release(); }
}

    }

    @Test
    void retainsTheCurrentEventOfASuppliedParser() throws Exception {
        JsonParser parser = Json.createParser(new StringReader("[1,2]"));
        parser.next();
        parser.next();

        {
JsonpParser reader = new JsonpParser(parser);
try {
            assertEquals(1, reader.readIntValue());
            assertEquals(2, reader.readIntValue());
            reader.endArray();
            reader.endRead();
        
} finally { reader.release(); }
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
        JsonpParser reader = new JsonpParser(tracked);
        reader.release();
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
JsonpParser reader = new JsonpParser(truncated);
try {
            IOException error = assertThrows(IOException.class, reader::skipNode);
            assertTrue(error.getMessage().contains("composite end"));
        
} finally { reader.release(); }
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
JsonpParser reader = new JsonpParser(legacy);
try {
            reader.startArray();
            assertEquals(7, reader.readIntValue());
            reader.endArray();
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void readsBoundaryIntegerNumbers() throws Exception {
        {
JsonpParser reader = reader("[9223372036854775807,-9223372036854775808,9223372036854775808]");
try {
            reader.startArray();
            assertEquals(Long.MAX_VALUE, reader.readLongValue());
            assertEquals(Long.MIN_VALUE, reader.readLongValue());
            assertThrows(ArithmeticException.class, reader::readLongValue);
            reader.endArray();
        
} finally { reader.release(); }
}
    }

    private static JsonpParser reader(String json) {
        return new JsonpParser(Json.createParser(new StringReader(json)));
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
