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
            Jackson2Parser reader =
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

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsPrimitiveCharsUsingExactOneCharacterSemantics() throws Exception {
        {
            Jackson2Parser reader = reader("[\"x\",null,\"\",\"xy\"]");
            try {
                reader.startArray();
                assertEquals('x', reader.readCharValue());

                BindingException nullError = assertThrows(BindingException.class, reader::readCharValue);
                assertEquals("cannot read null as char", nullError.getMessage());
                BindingException emptyError = assertThrows(BindingException.class, reader::readCharValue);
                assertEquals("cannot read char: expected single-character string, but length was 0",
                        emptyError.getMessage());
                BindingException multipleError = assertThrows(BindingException.class, reader::readCharValue);
                assertEquals("cannot read char: expected single-character string, but length was 2",
                        multipleError.getMessage());
                reader.endArray();

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsBoxedCharsUsingExactOneCharacterSemantics() throws Exception {
        {
            Jackson2Parser reader = reader("[\"x\",null,\"\",\"xy\"]");
            try {
                reader.startArray();
                assertEquals(Character.valueOf('x'), reader.readChar());
                assertNull(reader.readChar());
                BindingException emptyError = assertThrows(BindingException.class, reader::readChar);
                assertEquals("cannot read char: expected single-character string, but length was 0",
                        emptyError.getMessage());
                BindingException multipleError = assertThrows(BindingException.class, reader::readChar);
                assertEquals("cannot read char: expected single-character string, but length was 2",
                        multipleError.getMessage());
                reader.endArray();

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void preservesPrefetchedTokensAcrossConditionalOperations() throws Exception {
        {
            Jackson2Parser reader = reader("{\"id\":7,\"name\":\"Ada\"}");
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

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void preservesPrefetchedArrayElementsAcrossConditionalOperations() throws Exception {
        {
            Jackson2Parser reader = reader("[null]");
            try {
                assertTrue(reader.nextIfArrayStart());
                assertFalse(reader.nextIfArrayEnd());
                assertEquals(StreamingParser.Token.NULL, reader.peekToken());
                assertTrue(reader.nextIfNull());
                assertTrue(reader.nextIfArrayEnd());
                reader.endRead();

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void usesNativeExpectedNameAndValueMethods() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.writableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");
        CountingParser parser = new CountingParser(
                new JsonFactory().createParser("{\"id\":7,\"name\":\"Ada\"}"));

        {
            Jackson2Parser reader = new Jackson2Parser(parser);
            try {
                reader.startObject();
                assertEquals(id, reader.nextNameMatch(matcher, id));
                assertEquals(7, reader.readIntValue());
                assertEquals(name, reader.nextNameMatch(matcher, name));
                assertEquals("Ada", reader.readString());
                assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
                reader.endRead();

            } finally {
                reader.release();
            }
        }

        assertEquals(2, parser.expectedNameCalls);
        assertEquals(1, parser.intValueCalls);
        assertEquals(1, parser.textValueCalls);
    }

    @Test
    void matchesUnknownNamesAndSkipsTheirPendingValues() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.writableProperties);

        {
            Jackson2Parser reader = reader("{\"unknown\":[1,{\"nested\":true}],\"id\":7}");
            try {
                reader.startObject();
                assertEquals(NameMatcher.UNKNOWN, reader.nextNameMatch(matcher));
                reader.skipNode();
                assertEquals(matcher.fallback("id"), reader.nextNameMatch(matcher));
                assertEquals(7, reader.readIntValue());
                assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
                reader.endRead();

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void fallsBackAfterAnExpectedNameMismatchWithoutAdvancingTheValue() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.writableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        {
            Jackson2Parser reader = reader("{\"name\":\"Ada\",\"id\":7}");
            try {
                reader.startObject();
                assertEquals(name, reader.nextNameMatch(matcher, id));
                assertEquals("Ada", reader.readString());
                assertEquals(id, reader.nextNameMatch(matcher, id));
                assertEquals(7, reader.readIntValue());
                assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
                reader.endRead();

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsRawNodesWithSjf4jCollectionSemantics() throws Exception {
        {
            Jackson2Parser reader = reader(
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
                assertEquals(LinkedHashMap.class, ((List<?>) value.get("items")).get(1).getClass());
                assertEquals(StreamingParser.Token.EOF, reader.peekToken());
                reader.endRead();

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void rejectsEmptyRawNodeInput() throws Exception {
        {
            Jackson2Parser reader = reader("");
            try {
                assertThrows(IOException.class, reader::readRaw);

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsCanonicalUuidWithoutStringAndKeepsPrefetchedState() throws Exception {
        String text = "123e4567-e89b-12d3-a456-426614174000";
        java.util.UUID uuid = java.util.UUID.fromString(text);
        {
            Jackson2Parser reader = reader("[\"" + text + "\",null,\"" + text + "\"]");
            try {
                reader.startArray();
                assertEquals(StreamingParser.Token.STRING, reader.peekToken());
                assertEquals(uuid, reader.readUuid());
                assertNull(reader.readUuid());
                assertFalse(reader.nextIfNull());
                assertEquals(uuid, reader.readUuid());
                reader.endArray();
                reader.endRead();

            } finally {
                reader.release();
            }
        }

        {
            Jackson2Parser reader = reader("{\"id\":\"" + text + "\"}");
            try {
                reader.startObject();
                assertEquals("id", reader.nextName());
                assertEquals(uuid, reader.readUuid());
                reader.endObject();
                reader.endRead();

            } finally {
                reader.release();
            }
        }

        {
            Jackson2Parser reader = reader("[\"invalid\",42]");
            try {
                reader.startArray();
                assertThrows(IllegalArgumentException.class, reader::readUuid);
                assertThrows(IOException.class, reader::readUuid);
                reader.endArray();

            } finally {
                reader.release();
            }
        }
    }

    @Test
    void readsUuidFromUtf8Parser() throws Exception {
        String text = "ffffffff-0000-1234-abcd-000000000000";
        {
            Jackson2Parser reader = new Jackson2Parser(
                new JsonFactory().createParser(("\"" + text + "\"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            try {
                assertEquals(java.util.UUID.fromString(text), reader.readUuid());
                reader.endRead();

            } finally {
                reader.release();
            }
        }
    }

    private static Jackson2Parser reader(String json) throws IOException {
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
