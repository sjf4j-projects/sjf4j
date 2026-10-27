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
            Jackson3Parser parser =
                     parser("{\"id\":7,\"unknown\":true,\"name\":\"Ada\"}");
            try {
                parser.readObjectStart();

                // A failed conditional probe physically advances onto FIELD_NAME.
                assertFalse(parser.nextIfObjectEnd());
                assertEquals(0, parser.nextNameMatch(matcher));
                assertEquals(7, parser.readIntValue());

                assertEquals(StreamingParser.Token.NAME, parser.peekToken());
                assertEquals(NameMatcher.UNKNOWN, parser.nextNameMatch(matcher));
                assertEquals("unknown", parser.currentName());
                assertTrue(parser.readBooleanValue());

                assertFalse(parser.nextIfObjectEnd());
                assertEquals(1, parser.nextNameMatch(matcher));
                assertEquals("Ada", parser.readString());

                assertEquals(NameMatcher.OBJECT_END, parser.nextNameMatch(matcher));
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void preservesPeekAndConditionalStateAcrossObjectsAndArrays() throws Exception {
        {
            Jackson3Parser parser = parser("{\"id\":7,\"name\":\"Ada\"}");
            try {
                assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
                assertFalse(parser.nextIfNull());
                assertTrue(parser.nextIfObjectStart());
                assertFalse(parser.nextIfArrayStart());
                assertEquals(StreamingParser.Token.NAME, parser.peekToken());
                assertEquals("id", parser.nextName());
                assertFalse(parser.nextIfNull());
                assertEquals(StreamingParser.Token.NUMBER, parser.peekToken());
                assertEquals(7, parser.readIntValue());
                assertFalse(parser.nextIfObjectEnd());
                assertEquals("name", parser.nextName());
                assertEquals("Ada", parser.readString());
                assertTrue(parser.nextIfObjectEnd());
                parser.end();

            } finally {
                parser.release();
            }
        }

        {
            Jackson3Parser parser = parser("[null]");
            try {
                assertTrue(parser.nextIfArrayStart());
                assertFalse(parser.nextIfArrayEnd());
                assertEquals(StreamingParser.Token.NULL, parser.peekToken());
                assertTrue(parser.nextIfNull());
                assertTrue(parser.nextIfArrayEnd());
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void consumesTheCurrentTokenOfASuppliedParser() throws Exception {
        JsonParser nativeParser = new JsonFactory().createParser(
                ObjectReadContext.empty(), "{\"id\":7}");
        nativeParser.nextToken();

        {
            Jackson3Parser parser = new Jackson3Binder(new JsonFactory()).createParser(nativeParser);
            try {
                parser.readObjectStart();
                assertEquals("id", parser.nextName());
                assertEquals(7, parser.readIntValue());
                parser.readObjectEnd();
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void matchesCanonicalNamesNativelyAndAliasesThroughFallback() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson3NameMatcher(pojoInfo.settableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        {
            Jackson3Parser parser = parser("{\"unknown\":[1,{\"nested\":true}],\"legacyName\":\"Ada\",\"id\":7}");
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
                parser.release();
            }
        }
    }

    @Test
    void readsScalarsWithCoreNumericAndCharacterSemantics() throws Exception {
        {
            Jackson3Parser parser = parser("[1.9,128,1.25e2,123456789012345678901234567890,1.20e-3,\"x\",null,\"\",\"xy\"]");
            try {
                parser.readArrayStart();
                assertEquals(1, parser.readIntValue());
                assertThrows(ArithmeticException.class, parser::readByteValue);
                assertEquals(125d, parser.readDoubleValue());
                assertEquals(new BigInteger("123456789012345678901234567890"), parser.readBigInteger());
                assertEquals(new BigDecimal("0.0012"), parser.readBigDecimal());
                assertEquals('x', parser.readCharValue());
                assertThrows(BindingException.class, parser::readCharValue);
                assertThrows(BindingException.class, parser::readCharValue);
                assertThrows(BindingException.class, parser::readCharValue);
                parser.readArrayEnd();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsPrimitiveIntsAndLongsWithoutChangingNumericSemantics() throws Exception {
        {
            Jackson3Parser parser = parser("[0,-1,1.9,2147483647,-2147483648,9223372036854775807]");
            try {
                parser.readArrayStart();
                assertEquals(0, parser.readIntValue());
                assertEquals(-1L, parser.readLongValue());
                assertEquals(1, parser.readIntValue());
                assertEquals(Integer.MAX_VALUE, parser.readIntValue());
                assertEquals(Integer.MIN_VALUE, parser.readIntValue());
                assertEquals(Long.MAX_VALUE, parser.readLongValue());
                parser.readArrayEnd();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsRawNodesAndRejectsTrailingDocuments() throws Exception {
        {
            Jackson3Parser parser = parser(
                "{\"text\":\"Ada\",\"number\":7,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}");
            try {
                Map<?, ?> value = (Map<?, ?>) parser.readRaw();

                assertEquals(LinkedHashMap.class, value.getClass());
                assertEquals(Arrays.asList("text", "number", "enabled", "empty", "nested", "items"),
                        Arrays.asList(value.keySet().toArray()));
                assertEquals("Ada", value.get("text"));
                assertEquals(7, value.get("number"));
                assertEquals(true, value.get("enabled"));
                assertNull(value.get("empty"));
                assertEquals(LinkedHashMap.class, value.get("nested").getClass());
                assertEquals(ArrayList.class, value.get("items").getClass());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.release();
            }
        }

        {
            Jackson3Parser parser = parser("null null");
            try {
                assertTrue(parser.nextIfNull());
                assertThrows(IOException.class, parser::end);

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsCanonicalUuidWithoutStringAndKeepsPrefetchedState() throws Exception {
        String text = "123e4567-e89b-12d3-a456-426614174000";
        java.util.UUID uuid = java.util.UUID.fromString(text);
        {
            Jackson3Parser parser = parser("[\"" + text + "\",null,\"" + text + "\"]");
            try {
                parser.readArrayStart();
                assertEquals(StreamingParser.Token.STRING, parser.peekToken());
                assertEquals(uuid, parser.readUuid());
                assertNull(parser.readUuid());
                assertFalse(parser.nextIfNull());
                assertEquals(uuid, parser.readUuid());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.release();
            }
        }

        {
            Jackson3Parser parser = parser("{\"id\":\"" + text + "\"}");
            try {
                parser.readObjectStart();
                assertEquals("id", parser.nextName());
                assertEquals(uuid, parser.readUuid());
                parser.readObjectEnd();
                parser.end();

            } finally {
                parser.release();
            }
        }

        {
            Jackson3Parser parser = parser("[\"invalid\",42]");
            try {
                parser.readArrayStart();
                assertThrows(IllegalArgumentException.class, parser::readUuid);
                assertThrows(IOException.class, parser::readUuid);
                parser.readArrayEnd();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsUuidFromUtf8Parser() throws Exception {
        String text = "ffffffff-0000-1234-abcd-000000000000";
        {
            Jackson3Parser parser = new Jackson3Parser(
                new JsonFactory().createParser(ObjectReadContext.empty(),
                        ("\"" + text + "\"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            try {
                assertEquals(java.util.UUID.fromString(text), parser.readUuid());
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    private static Jackson3Parser parser(String json) throws IOException {
        return new Jackson3Parser(new JsonFactory().createParser(ObjectReadContext.empty(), json));
    }

    static class User {
        public int id;

        @NodeProperty(aliases = "legacyName")
        public String name;
    }
}
