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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
            }
        }
    }

    @Test
    void decodesCanonicalLocalDatesAndRetainsIsoFallback() throws Exception {
        String[] dates = {
                "2026-10-31", "2000-02-29", "0000-01-01",
                "9999-12-31", "+10000-01-01", "-0001-12-31"
        };
        for (boolean utf8 : new boolean[]{false, true}) {
            for (String date : dates) {
                String json = "[\"" + date + "\",null]";
                try (Jackson2Parser parser = utf8
                        ? new Jackson2Parser(new JsonFactory().createParser(
                                json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        : parser(json)) {
                    parser.readArrayStart();
                    assertFalse(parser.nextIfArrayEnd());
                    assertEquals(LocalDate.parse(date), parser.readLocalDate());
                    assertNull(parser.readLocalDate());
                    parser.readArrayEnd();
                    parser.end();
                }
            }
        }

        try (Jackson2Parser parser = parser("[\\\"2023-02-29\\\",7]")) {
            parser.readArrayStart();
            assertThrows(DateTimeParseException.class, parser::readLocalDate);
            assertThrows(IOException.class, parser::readLocalDate);
            parser.readArrayEnd();
            parser.end();
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
                parser.close();
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
                parser.close();
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
                parser.close();
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
                parser.close();
            }
        }
    }

    @Test
    void readsTypedRawObjectsAndArraysWithoutLosingPendingTokens() throws Exception {
        Jackson2Parser parser = parser("[{\"id\":7,\"list\":[true,null]},[1,{\"x\":2}]]");
        try {
            parser.readArrayStart();
            assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
            Map<String, Object> object = parser.readRawObject();
            assertEquals(LinkedHashMap.class, object.getClass());
            assertEquals(7, object.get("id"));
            assertEquals(ArrayList.class, object.get("list").getClass());
            assertFalse(parser.nextIfArrayEnd());
            List<Object> array = parser.readRawArray();
            assertEquals(ArrayList.class, array.getClass());
            assertEquals(LinkedHashMap.class, array.get(1).getClass());
            parser.readArrayEnd();
            parser.end();
        } finally {
            parser.close();
        }

        Jackson2Parser nullParser = parser("[null,null]");
        try {
            nullParser.readArrayStart();
            assertNull(nullParser.readRawObject());
            assertNull(nullParser.readRawArray());
            nullParser.readArrayEnd();
            nullParser.end();
        } finally {
            nullParser.close();
        }

        Jackson2Parser mismatch = parser("[]");
        try {
            assertThrows(BindingException.class, mismatch::readRawObject);
        } finally {
            mismatch.close();
        }
    }

    @Test
    void readsRawCollectionsAcrossSizes() throws Exception {
        for (int count = 0; count <= 8; count++) {
            StringBuilder objectJson = new StringBuilder("{");
            StringBuilder arrayJson = new StringBuilder("[");
            Map<String, Object> expectedObject = new LinkedHashMap<>();
            List<Object> expectedArray = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (i != 0) {
                    objectJson.append(',');
                    arrayJson.append(',');
                }
                objectJson.append('"').append("k").append(i).append("\":").append(i);
                arrayJson.append(i);
                expectedObject.put("k" + i, i);
                expectedArray.add(i);
            }
            objectJson.append('}');
            arrayJson.append(']');

            try (Jackson2Parser p = parser(objectJson.toString())) {
                Map<String, Object> value = p.readRawObject();
                assertEquals(LinkedHashMap.class, value.getClass());
                assertEquals(expectedObject, value);
                assertEquals(new ArrayList<>(expectedObject.keySet()),
                        new ArrayList<>(value.keySet()));
                p.end();
            }
            try (Jackson2Parser p = parser(arrayJson.toString())) {
                List<Object> value = p.readRawArray();
                assertEquals(ArrayList.class, value.getClass());
                assertEquals(expectedArray, value);
                p.end();
            }
        }

        try (Jackson2Parser p = parser("{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5,\"a\":6,\"f\":[null,{\"ok\":true}]}")) {
            Map<String, Object> value = p.readRawObject();
            assertEquals(Arrays.asList("a", "b", "c", "d", "e", "f"),
                    new ArrayList<>(value.keySet()));
            assertEquals(6, value.get("a"));
            assertEquals(2, ((List<?>) value.get("f")).size());
            p.end();
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
    @Test
    void readsNestedRawValuesAfterArrayEndProbes() throws Exception {
        String json = "[null,{\"a\":[1,true,{\"b\":null}]},[\"x\",2],false]";
        Jackson2Binder binder = new Jackson2Binder();
        for (boolean utf8 : new boolean[]{false, true}) {
            try (Jackson2Parser parser = utf8
                    ? binder.createParser(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    : binder.createParser(json)) {
                parser.readArrayStart();

                assertFalse(parser.nextIfArrayEnd());
                assertNull(parser.readRaw());

                assertFalse(parser.nextIfArrayEnd());
                Map<?, ?> object = (Map<?, ?>) parser.readRaw();
                assertEquals(LinkedHashMap.class, object.getClass());
                List<?> nested = (List<?>) object.get("a");
                assertEquals(3, nested.size());
                assertEquals(1, nested.get(0));
                assertEquals(true, nested.get(1));
                assertNull(((Map<?, ?>) nested.get(2)).get("b"));

                assertFalse(parser.nextIfArrayEnd());
                assertEquals(Arrays.asList("x", 2), parser.readRaw());

                assertFalse(parser.nextIfArrayEnd());
                assertEquals(false, parser.readRaw());

                assertTrue(parser.nextIfArrayEnd());
                parser.end();
            }
        }
    }


}
