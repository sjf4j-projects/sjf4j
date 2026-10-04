package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.JsonArray;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.binding.StreamingIOV4;
import org.sjf4j.binding.StreamingReaderV4;
import org.sjf4j.binding.StreamingReaderV4.NameMatcher;
import org.sjf4j.node.TypeRegistry;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Jackson2ReaderV4Test {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void expectedNamePathLeavesTheMatchedValuePending() throws Exception {
        TypeRegistry.registerTypeInfo(User.class);
        try (Jackson2ReaderV4 reader = reader("{\"id\":7,\"name\":\"Ada\"}")) {
            reader.startDocument();
            reader.startObject();

            NameMatcher matcher = reader.nameMatcher(User.class);
            int id = matcher.match("id");
            int name = matcher.match("name");
            assertEquals(id, reader.nextNameMatch(matcher, id));
            assertEquals(7, reader.readIntValue());
            assertEquals(StreamingReaderV4.Token.NAME, reader.peekToken());

            assertEquals(name, reader.nextNameMatch(matcher, name));
            assertEquals("Ada", reader.readString());
            assertEquals(StreamingReaderV4.Token.END_OBJECT, reader.peekToken());
            assertEquals(true, reader.nextIfObjectEnd());
            reader.endDocument();
        }
    }

    @Test
    void expectedNameMismatchConsumesTheActualFieldOnlyOnce() throws Exception {
        TypeRegistry.registerTypeInfo(User.class);
        try (Jackson2ReaderV4 reader = reader("{\"name\":\"Ada\",\"id\":7}")) {
            reader.startDocument();
            reader.startObject();

            NameMatcher matcher = reader.nameMatcher(User.class);
            int id = matcher.match("id");
            int name = matcher.match("name");
            assertEquals(name, reader.nextNameMatch(matcher, id));
            assertEquals("Ada", reader.readString());
            assertEquals(id, reader.nextNameMatch(matcher, -1));
            assertEquals(7, reader.readIntValue());
            assertEquals(true, reader.nextIfObjectEnd());
            reader.endDocument();
        }
    }

    @Test
    void expectedNameMatchUsesJacksonFastPath() throws Exception {
        TypeRegistry.registerTypeInfo(User.class);
        CountingParser parser = new CountingParser(MAPPER.getFactory().createParser("{\"id\":7,\"name\":\"Ada\"}"));
        try (Jackson2ReaderV4 reader = new Jackson2ReaderV4(parser)) {
            reader.startObject();
            NameMatcher matcher = reader.nameMatcher(User.class);
            int id = matcher.match("id");
            int name = matcher.match("name");

            assertEquals(id, reader.nextNameMatch(matcher, id));
            assertEquals(7, reader.readIntValue());
            assertEquals(name, reader.nextNameMatch(matcher, name));
            assertEquals("Ada", reader.readString());
            assertEquals(NameMatcher.END_OF_OBJECT, reader.nextNameMatch(matcher, -1));
            assertEquals(2, parser.expectedNameCalls);
            reader.endDocument();
        }
    }

    @Test
    void targetDirectedIoReadsNestedPojoCollectionAndUnknownField() throws Exception {
        try (Jackson2ReaderV4 reader = reader(
                "{\"id\":7,\"name\":\"Ada\",\"tags\":[\"a\",\"b\"]," +
                        "\"address\":{\"city\":\"Paris\"},\"ignored\":true}")) {
            reader.startDocument();
            User user = (User) StreamingIOV4.readNode(reader, User.class, RuntimeContext.EMPTY);
            reader.endDocument();

            assertEquals(7, user.id);
            assertEquals("Ada", user.name);
            assertEquals(List.of("a", "b"), user.tags);
            assertEquals("Paris", user.address.city);
        }
    }

    @Test
    void targetDirectedIoKeepsBoxedCharacterEmptyStringCompatibility() throws Exception {
        try (Jackson2ReaderV4 reader = reader("{\"value\":\"\"}")) {
            reader.startDocument();
            CharacterValue value = (CharacterValue) StreamingIOV4.readNode(
                    reader, CharacterValue.class, RuntimeContext.EMPTY);
            reader.endDocument();
            assertNull(value.value);
        }
    }

    @Test
    void emptyInputIsRejectedOnRead() throws Exception {
        try (Jackson2ReaderV4 reader = reader("")) {
            IOException error = assertThrows(IOException.class, reader::readRawNode);
        }
    }

    @Test
    void nullableNumberConsumesNull() throws Exception {
        try (Jackson2ReaderV4 reader = reader("null")) {
            reader.startDocument();
            assertNull(reader.readNumber());
            reader.endDocument();
        }
    }

    @Test
    void targetDirectedIoSupportsCurrentAndParentOneOfFields() throws Exception {
        try (Jackson2ReaderV4 currentReader = reader("{\"pet\":{\"kind\":\"dog\",\"barks\":true}}")) {
            currentReader.startDocument();
            CurrentOneOf value = (CurrentOneOf) StreamingIOV4.readNode(
                    currentReader, CurrentOneOf.class, RuntimeContext.EMPTY);
            currentReader.endDocument();
            assertEquals(true, assertInstanceOf(Dog.class, value.pet).barks);
        }
        try (Jackson2ReaderV4 parentReader = reader("{\"pet\":{\"barks\":true},\"kind\":\"dog\"}")) {
            parentReader.startDocument();
            ParentOneOf value = (ParentOneOf) StreamingIOV4.readNode(
                    parentReader, ParentOneOf.class, RuntimeContext.EMPTY);
            parentReader.endDocument();
            assertEquals(true, assertInstanceOf(Dog.class, value.pet).barks);
        }
    }

    @Test
    void targetDirectedIoReadsJsonArraySubclasses() throws Exception {
        try (Jackson2ReaderV4 reader = reader("[1,2]")) {
            reader.startDocument();
            IntegerArray value = (IntegerArray) StreamingIOV4.readNode(
                    reader, IntegerArray.class, RuntimeContext.EMPTY);
            reader.endDocument();
            assertEquals(2, value.size());
            assertEquals(2, value.getInt(1));
        }
    }

    @Test
    void creatorArgumentsKeepDuplicatePropertyValidation() throws Exception {
        try (Jackson2ReaderV4 reader = reader("{\"name\":\"first\",\"name\":\"second\"}")) {
            reader.startDocument();
            assertThrows(Exception.class, () -> StreamingIOV4.readNode(
                    reader, CreatorValue.class, RuntimeContext.EMPTY));
        }
    }

    @Test
    void jsonContainerTargetsRejectMismatchedSourceShapesBeforeConsumption() throws Exception {
        try (Jackson2ReaderV4 objectReader = reader("[]");
             Jackson2ReaderV4 arrayReader = reader("{}")) {
            objectReader.startDocument();
            arrayReader.startDocument();
            assertThrows(Exception.class, () -> StreamingIOV4.readNode(
                    objectReader, org.sjf4j.JsonObject.class, RuntimeContext.EMPTY));
            assertThrows(Exception.class, () -> StreamingIOV4.readNode(
                    arrayReader, JsonArray.class, RuntimeContext.EMPTY));
        }
    }

    private static Jackson2ReaderV4 reader(String json) throws Exception {
        return new Jackson2ReaderV4(MAPPER.getFactory().createParser(json));
    }

    private static final class CountingParser extends JsonParserDelegate {
        int expectedNameCalls;

        CountingParser(JsonParser delegate) {
            super(delegate);
        }

        @Override
        public boolean nextFieldName(SerializableString name) throws IOException {
            expectedNameCalls++;
            return super.nextFieldName(name);
        }
    }

    static class User {
        public int id;
        public String name;
        public List<String> tags;
        public Address address;
    }

    static class Address {
        public String city;
    }

    static class CharacterValue {
        public Character value;
    }

    static class CurrentOneOf {
        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog")}, key = "kind")
        public Animal pet;
    }

    static class ParentOneOf {
        public String kind;

        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog")},
                key = "kind", scope = OneOf.Scope.PARENT)
        public Animal pet;
    }

    static class Animal {
    }

    static class Dog extends Animal {
        public boolean barks;
    }

    static class IntegerArray extends JsonArray {
        @Override
        public Class<?> elementClass() {
            return Integer.class;
        }
    }

    static class CreatorValue {
        final String name;

        @NodeCreator
        CreatorValue(@NodeProperty("name") String name) {
            this.name = name;
        }
    }
}
