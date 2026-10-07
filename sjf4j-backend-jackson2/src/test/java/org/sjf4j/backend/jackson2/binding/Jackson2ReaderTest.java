package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.TypeRegistry;

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

class Jackson2ReaderTest {

    @Test
    void readsPrimitiveCharsUsingExactOneCharacterSemantics() throws Exception {
        try (Jackson2Reader reader = reader("[\"x\",null,\"\",\"xy\"]")) {
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
        }
    }

    @Test
    void readsBoxedCharsUsingExactOneCharacterSemantics() throws Exception {
        try (Jackson2Reader reader = reader("[\"x\",null,\"\",\"xy\"]")) {
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
        }
    }

    @Test
    void preservesPrefetchedTokensAcrossConditionalOperations() throws Exception {
        try (Jackson2Reader reader = reader("{\"id\":7,\"name\":\"Ada\"}")) {
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
    }

    @Test
    void preservesPrefetchedArrayElementsAcrossConditionalOperations() throws Exception {
        try (Jackson2Reader reader = reader("[null]")) {
            assertTrue(reader.nextIfArrayStart());
            assertFalse(reader.nextIfArrayEnd());
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
            reader.endDocument();
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

        try (Jackson2Reader reader = new Jackson2Reader(parser)) {
            reader.startObject();
            assertEquals(id, reader.nextNameMatch(matcher, id));
            assertEquals(7, reader.readIntValue());
            assertEquals(name, reader.nextNameMatch(matcher, name));
            assertEquals("Ada", reader.readString());
            assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
            reader.endDocument();
        }

        assertEquals(2, parser.expectedNameCalls);
        assertEquals(1, parser.intValueCalls);
        assertEquals(1, parser.textValueCalls);
    }

    @Test
    void matchesUnknownNamesAndSkipsTheirPendingValues() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.writableProperties);

        try (Jackson2Reader reader = reader("{\"unknown\":[1,{\"nested\":true}],\"id\":7}")) {
            reader.startObject();
            assertEquals(NameMatcher.UNKNOWN, reader.nextNameMatch(matcher));
            reader.skipNode();
            assertEquals(matcher.fallback("id"), reader.nextNameMatch(matcher));
            assertEquals(7, reader.readIntValue());
            assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
            reader.endDocument();
        }
    }

    @Test
    void fallsBackAfterAnExpectedNameMismatchWithoutAdvancingTheValue() throws Exception {
        PojoInfo pojoInfo = TypeRegistry.requireRegisteredPojoInfo(User.class);
        NameMatcher matcher = new Jackson2NameMatcher(pojoInfo.writableProperties);
        int id = matcher.fallback("id");
        int name = matcher.fallback("name");

        try (Jackson2Reader reader = reader("{\"name\":\"Ada\",\"id\":7}")) {
            reader.startObject();
            assertEquals(name, reader.nextNameMatch(matcher, id));
            assertEquals("Ada", reader.readString());
            assertEquals(id, reader.nextNameMatch(matcher, id));
            assertEquals(7, reader.readIntValue());
            assertEquals(NameMatcher.OBJECT_END, reader.nextNameMatch(matcher));
            reader.endDocument();
        }
    }

    @Test
    void readsRawNodesWithSjf4jCollectionSemantics() throws Exception {
        try (Jackson2Reader reader = reader(
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
            assertEquals(LinkedHashMap.class, ((List<?>) value.get("items")).get(1).getClass());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void rejectsEmptyRawNodeInput() throws Exception {
        try (Jackson2Reader reader = reader("")) {
            assertThrows(IOException.class, reader::readRawNode);
        }
    }

    private static Jackson2Reader reader(String json) throws IOException {
        return new Jackson2Reader(new JsonFactory().createParser(json));
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
