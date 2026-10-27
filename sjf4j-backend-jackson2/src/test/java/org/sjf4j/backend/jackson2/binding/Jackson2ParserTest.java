package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;
import org.sjf4j.meta.PojoInfo;
import org.sjf4j.meta.TypeRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson2ParserTest {

    @Test
    void matchesPrefetchedFieldNamesWithoutLosingThePendingValue() throws Exception {
        NameMatcher matcher = new Jackson2NameMatcher("id", "name");

        {
            Jackson2Parser parser =
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
    void readsPrimitiveCharsUsingExactOneCharacterSemantics() throws Exception {
        {
            Jackson2Parser parser = parser("[\"x\",null,\"\",\"xy\"]");
            try {
                parser.readArrayStart();
                assertEquals('x', parser.readCharValue());

                BindingException nullError = assertThrows(BindingException.class, parser::readCharValue);
                assertEquals("cannot read null as char", nullError.getMessage());
                BindingException emptyError = assertThrows(BindingException.class, parser::readCharValue);
                assertEquals("cannot read char: expected single-character string, but length was 0",
                        emptyError.getMessage());
                BindingException multipleError = assertThrows(BindingException.class, parser::readCharValue);
                assertEquals("cannot read char: expected single-character string, but length was 2",
                        multipleError.getMessage());
                parser.readArrayEnd();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsBoxedCharsUsingExactOneCharacterSemantics() throws Exception {
        {
            Jackson2Parser parser = parser("[\"x\",null,\"\",\"xy\"]");
            try {
                parser.readArrayStart();
                assertEquals(Character.valueOf('x'), parser.readChar());
                assertNull(parser.readChar());
                BindingException emptyError = assertThrows(BindingException.class, parser::readChar);
                assertEquals("cannot read char: expected single-character string, but length was 0",
                        emptyError.getMessage());
                BindingException multipleError = assertThrows(BindingException.class, parser::readChar);
                assertEquals("cannot read char: expected single-character string, but length was 2",
                        multipleError.getMessage());
                parser.readArrayEnd();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void preservesPrefetchedTokensAcrossConditionalOperations() throws Exception {
        {
            Jackson2Parser parser = parser("{\"id\":7,\"name\":\"Ada\"}");
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
    }

    @Test
    void preservesPrefetchedArrayElementsAcrossConditionalOperations() throws Exception {
        {
            Jackson2Parser parser = parser("[null]");
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
    void usesNativeExpectedNameAndValueMethods() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.settableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");
        CountingParser nativeParser = new CountingParser(
                new JsonFactory().createParser("{\"id\":7,\"name\":\"Ada\"}"));

        {
            Jackson2Parser parser = new Jackson2Parser(nativeParser);
            try {
                parser.readObjectStart();
                assertEquals(id, parser.nextNameMatch(matcher, id));
                assertEquals(7, parser.readIntValue());
                assertEquals(name, parser.nextNameMatch(matcher, name));
                assertEquals("Ada", parser.readString());
                assertEquals(NameMatcher.OBJECT_END, parser.nextNameMatch(matcher));
                parser.end();

            } finally {
                parser.release();
            }
        }

        assertEquals(2, nativeParser.expectedNameCalls);
        assertEquals(1, nativeParser.intValueCalls);
        assertEquals(1, nativeParser.textValueCalls);
    }

    @Test
    void matchesUnknownNamesAndSkipsTheirPendingValues() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.settableProperties);

        {
            Jackson2Parser parser = parser("{\"unknown\":[1,{\"nested\":true}],\"id\":7}");
            try {
                parser.readObjectStart();
                assertEquals(NameMatcher.UNKNOWN, parser.nextNameMatch(matcher));
                parser.skipNode();
                assertEquals(matcher.fallback("id"), parser.nextNameMatch(matcher));
                assertEquals(7, parser.readIntValue());
                assertEquals(NameMatcher.OBJECT_END, parser.nextNameMatch(matcher));
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void fallsBackAfterAnExpectedNameMismatchWithoutAdvancingTheValue() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.settableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        {
            Jackson2Parser parser = parser("{\"name\":\"Ada\",\"id\":7}");
            try {
                parser.readObjectStart();
                assertEquals(name, parser.nextNameMatch(matcher, id));
                assertEquals("Ada", parser.readString());
                assertEquals(id, parser.nextNameMatch(matcher, id));
                assertEquals(7, parser.readIntValue());
                assertEquals(NameMatcher.OBJECT_END, parser.nextNameMatch(matcher));
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void readsRawNodesWithSjf4jCollectionSemantics() throws Exception {
        {
            Jackson2Parser parser = parser(
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
                assertEquals(LinkedHashMap.class, ((List<?>) value.get("items")).get(1).getClass());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    @Test
    void rejectsEmptyRawNodeInput() throws Exception {
        {
            Jackson2Parser parser = parser("");
            try {
                assertThrows(IOException.class, parser::readRaw);

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
            Jackson2Parser parser = parser("[\"" + text + "\",null,\"" + text + "\"]");
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
            Jackson2Parser parser = parser("{\"id\":\"" + text + "\"}");
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
            Jackson2Parser parser = parser("[\"invalid\",42]");
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
            Jackson2Parser parser = new Jackson2Parser(
                new JsonFactory().createParser(("\"" + text + "\"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            try {
                assertEquals(java.util.UUID.fromString(text), parser.readUuid());
                parser.end();

            } finally {
                parser.release();
            }
        }
    }

    private static Jackson2Parser parser(String json) throws IOException {
        return new Jackson2Parser(new JsonFactory().createParser(json));
    }

    private static final class CountingParser extends JsonParserDelegate {
        int expectedNameCalls;
        int intValueCalls;
        int textValueCalls;

        CountingParser(JsonParser delegate) {
            super(delegate);
        }

        @Override
        public boolean nextFieldName(SerializableString name) throws IOException {
            expectedNameCalls++;
            return super.nextFieldName(name);
        }

        @Override
        public int nextIntValue(int defaultValue) throws IOException {
            intValueCalls++;
            return super.nextIntValue(defaultValue);
        }

        @Override
        public String nextTextValue() throws IOException {
            textValueCalls++;
            return super.nextTextValue();
        }
    }

    static class User {
        public int id;
        public String name;
    }
}
