package org.sjf4j.backend.jackson3.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.TypeRegistry;
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

class Jackson3ParserTest {

    @Test
    void matchesPrefetchedFieldNamesWithoutLosingThePendingValue() throws Exception {
        NameMatcher matcher = new Jackson3NameMatcher("id", "name");

        {
Jackson3Parser reader =
                     reader("{\"id\":7,\"unknown\":true,\"name\":\"Ada\"}");
try {
            reader.startObject();

            // A failed conditional probe physically advances onto FIELD_NAME.
            assertFalse(reader.nextIfObjectEnd());
            assertEquals(0, reader.nextNameMatch(matcher));
            assertEquals(7, reader.readIntValue());

            assertEquals(StreamingParser.Token.NAME, reader.peekToken());
            assertEquals(NameMatcher.UNKNOWN, reader.nextNameMatch(matcher));
            assertEquals("unknown", reader.currentName());
            assertTrue(reader.readBooleanValue());

            assertFalse(reader.nextIfObjectEnd());
            assertEquals(1, reader.nextNameMatch(matcher));
            assertEquals("Ada", reader.readString());

            assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void preservesPeekAndConditionalStateAcrossObjectsAndArrays() throws Exception {
        {
Jackson3Parser reader = reader("{\"id\":7,\"name\":\"Ada\"}");
try {
            assertEquals(StreamingParser.Token.OBJECT_START, reader.peekToken());
            assertFalse(reader.nextIfNull());
            assertTrue(reader.nextIfObjectStart());
            assertFalse(reader.nextIfArrayStart());
            assertEquals(StreamingParser.Token.NAME, reader.peekToken());
            assertEquals("id", reader.nextName());
            assertFalse(reader.nextIfNull());
            assertEquals(StreamingParser.Token.NUMBER, reader.peekToken());
            assertEquals(7, reader.readIntValue());
            assertFalse(reader.nextIfObjectEnd());
            assertEquals("name", reader.nextName());
            assertEquals("Ada", reader.readString());
            assertTrue(reader.nextIfObjectEnd());
            reader.endRead();
        
} finally { reader.release(); }
}

        {
Jackson3Parser reader = reader("[null]");
try {
            assertTrue(reader.nextIfArrayStart());
            assertFalse(reader.nextIfArrayEnd());
            assertEquals(StreamingParser.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void consumesTheCurrentTokenOfASuppliedParser() throws Exception {
        JsonParser parser = new JsonFactory().createParser(
                ObjectReadContext.empty(), "{\"id\":7}");
        parser.nextToken();

        {
Jackson3Parser reader = new Jackson3Binder(new JsonFactory()).createParser(parser);
try {
            reader.startObject();
            assertEquals("id", reader.nextName());
            assertEquals(7, reader.readIntValue());
            reader.endObject();
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void matchesCanonicalNamesNativelyAndAliasesThroughFallback() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson3NameMatcher(pojoInfo.writableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        {
Jackson3Parser reader = reader("{\"unknown\":[1,{\"nested\":true}],\"legacyName\":\"Ada\",\"id\":7}");
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
Jackson3Parser reader = reader("[1.9,128,1.25e2,123456789012345678901234567890,1.20e-3,\"x\",null,\"\",\"xy\"]");
try {
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
        
} finally { reader.release(); }
}
    }

    @Test
    void readsPrimitiveIntsAndLongsWithoutChangingNumericSemantics() throws Exception {
        {
Jackson3Parser reader = reader("[0,-1,1.9,2147483647,-2147483648,9223372036854775807]");
try {
            reader.startArray();
            assertEquals(0, reader.readIntValue());
            assertEquals(-1L, reader.readLongValue());
            assertEquals(1, reader.readIntValue());
            assertEquals(Integer.MAX_VALUE, reader.readIntValue());
            assertEquals(Integer.MIN_VALUE, reader.readIntValue());
            assertEquals(Long.MAX_VALUE, reader.readLongValue());
            reader.endArray();
        
} finally { reader.release(); }
}
    }

    @Test
    void readsRawNodesAndRejectsTrailingDocuments() throws Exception {
        {
Jackson3Parser reader = reader(
                "{\"text\":\"Ada\",\"number\":7,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}");
try {
            Map<?, ?> value = (Map<?, ?>) reader.readRaw();

            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals("Ada", value.get("text"));
            assertEquals(7, value.get("number"));
            assertEquals(true, value.get("enabled"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(ArrayList.class, value.get("items").getClass());
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            reader.endRead();
        
} finally { reader.release(); }
}

        {
Jackson3Parser reader = reader("null null");
try {
            assertTrue(reader.nextIfNull());
            assertThrows(IOException.class, reader::endRead);
        
} finally { reader.release(); }
}
    }

    @Test
    void readsCanonicalUuidWithoutStringAndKeepsPrefetchedState() throws Exception {
        String text = "123e4567-e89b-12d3-a456-426614174000";
        java.util.UUID uuid = java.util.UUID.fromString(text);
        {
Jackson3Parser reader = reader("[\"" + text + "\",null,\"" + text + "\"]");
try {
            reader.startArray();
            assertEquals(StreamingParser.Token.STRING, reader.peekToken());
            assertEquals(uuid, reader.readUuid());
            assertNull(reader.readUuid());
            assertFalse(reader.nextIfNull());
            assertEquals(uuid, reader.readUuid());
            reader.endArray();
            reader.endRead();
        
} finally { reader.release(); }
}

        {
Jackson3Parser reader = reader("{\"id\":\"" + text + "\"}");
try {
            reader.startObject();
            assertEquals("id", reader.nextName());
            assertEquals(uuid, reader.readUuid());
            reader.endObject();
            reader.endRead();
        
} finally { reader.release(); }
}

        {
Jackson3Parser reader = reader("[\"invalid\",42]");
try {
            reader.startArray();
            assertThrows(IllegalArgumentException.class, reader::readUuid);
            assertThrows(IOException.class, reader::readUuid);
            reader.endArray();
        
} finally { reader.release(); }
}
    }

    @Test
    void readsUuidFromUtf8Parser() throws Exception {
        String text = "ffffffff-0000-1234-abcd-000000000000";
        {
Jackson3Parser reader = new Jackson3Parser(
                new JsonFactory().createParser(ObjectReadContext.empty(),
                        ("\"" + text + "\"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
try {
            assertEquals(java.util.UUID.fromString(text), reader.readUuid());
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    private static Jackson3Parser reader(String json) throws IOException {
        return new Jackson3Parser(new JsonFactory().createParser(ObjectReadContext.empty(), json));
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
