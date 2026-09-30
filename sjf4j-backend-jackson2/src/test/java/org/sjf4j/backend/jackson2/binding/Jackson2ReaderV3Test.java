package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.RuntimeContext;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.binding.NameMatcher;
import org.sjf4j.binding.StreamingIOV3;
import org.sjf4j.binding.StreamingReaderV3;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Jackson2ReaderV3Test {

    @Test
    void genericExpectedMatcherAdvancesToValuesAndUsesDistinctSentinels() throws Exception {
        assertEquals(-1, StreamingReaderV3.NO_EXPECTED_FIELD);
        assertEquals(-2, NameMatcher.UNKNOWN_FIELD);
        assertEquals(-3, StreamingReaderV3.END_OF_OBJECT);

        NameMatcher matcher = new NameMatcher() {
            @Override
            public String name(int index) {
                return index == 0 ? "first" : "second";
            }

            @Override
            public int match(String name) {
                return "second".equals(name) ? 1 : UNKNOWN_FIELD;
            }
        };
        try (Jackson2ReaderV3 reader = reader("{\"second\":2,\"other\":3}")) {
            reader.startDocument();
            reader.beginObject();

            assertEquals(1, reader.nextObjectField(matcher, 0));
            assertEquals(2, reader.readIntValue());
            assertEquals(NameMatcher.UNKNOWN_FIELD, reader.nextObjectField(matcher));
            assertEquals(3, reader.readIntValue());
            assertEquals(StreamingReaderV3.END_OF_OBJECT, reader.nextObjectField(matcher));
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void classMatcherUsesCachedPojoMetadata() throws Exception {
        try (Jackson2ReaderV3 reader = reader("{\"name\":\"v\"}")) {
            NameMatcher matcher = reader.nameMatcher(MatcherPojo.class);
            assertSame(matcher, reader.nameMatcher(MatcherPojo.class));

            reader.startDocument();
            reader.beginObject();
            assertEquals(0, reader.nextObjectField(matcher));
            assertEquals("v", reader.readString());
        }
    }

    @Test
    void objectNameTraversalAdvancesToValuesAndEndsAtObjectEnd() throws Exception {
        try (Jackson2ReaderV3 reader = reader("{\"first\":1,\"second\":2}")) {
            reader.startDocument();
            reader.beginObject();
            assertEquals("first", reader.nextObjectName());
            assertEquals(1, reader.readIntValue());
            assertEquals("second", reader.nextObjectName());
            assertEquals(2, reader.readIntValue());
            assertNull(reader.nextObjectName());
            assertEquals(StreamingReaderV3.Token.END_OBJECT, reader.currentToken());
        }
    }

    @Test
    void nestedTraversalLeavesCompletedChildAtItsEnd() throws Exception {
        Jackson2NameMatcherV3 outer = Jackson2NameMatcherV3.of("child", "after");
        Jackson2NameMatcherV3 child = Jackson2NameMatcherV3.of("value");
        try (Jackson2ReaderV3 reader = reader("{\"child\":{\"value\":1},\"after\":2}")) {
            reader.startDocument();
            reader.beginObject();
            assertEquals(0, reader.nextObjectField(outer));
            reader.beginObject();
            assertEquals(0, reader.nextObjectField(child));
            assertEquals(1, reader.readIntValue());
            assertEquals(StreamingReaderV3.END_OF_OBJECT, reader.nextObjectField(child));
            reader.endObject();
            assertEquals(1, reader.nextObjectField(outer));
            assertEquals(2, reader.readIntValue());
        }
    }

    @Test
    void orderedMismatchPreservesFieldOrEndForConsumptionFallback() throws Exception {
        Jackson2NameMatcherV3 matcher = Jackson2NameMatcherV3.of("first", "second");
        try (JsonParser parser = new JsonFactory().createParser("{\"second\":2}");
             Jackson2ReaderV3 reader = new Jackson2ReaderV3(parser)) {
            reader.startDocument();
            reader.beginObject();
            assertFalse(reader.nextExpectedName(matcher, 0));
            assertEquals(JsonToken.FIELD_NAME, parser.currentToken());
            assertEquals(StreamingReaderV3.Token.FIELD_NAME, reader.currentToken());
            assertEquals(1, reader.consumeCurrentObjectField(matcher));
            assertEquals(2, reader.readIntValue());
            assertFalse(reader.nextExpectedName(matcher, 0));
            assertEquals(JsonToken.END_OBJECT, parser.currentToken());
            assertEquals(StreamingReaderV3.END_OF_OBJECT,
                    reader.consumeCurrentObjectField(matcher));
        }
    }

    @Test
    void directConsumersAdvanceFromFieldNamesToValues() throws Exception {
        Jackson2NameMatcherV3 matcher = Jackson2NameMatcherV3.of(
                "long", "int", "boolean", "string", "double");
        try (Jackson2ReaderV3 reader = reader(
                "{\"long\":null,\"int\":null,\"boolean\":null,\"string\":null,\"double\":1.5}")) {
            reader.startDocument();
            reader.beginObject();
            assertEquals(true, reader.nextExpectedName(matcher, 0));
            assertEquals(StreamingReaderV3.Token.FIELD_NAME, reader.currentToken());
            assertEquals(0L, reader.nextLongValue());
            assertEquals(StreamingReaderV3.Token.NULL, reader.currentToken());
            assertEquals(true, reader.nextExpectedName(matcher, 1));
            assertEquals(0, reader.nextIntValue());
            assertEquals(true, reader.nextExpectedName(matcher, 2));
            assertFalse(reader.nextBooleanValue());
            assertEquals(true, reader.nextExpectedName(matcher, 3));
            assertNull(reader.nextStringOrNull());
            assertEquals(true, reader.nextExpectedName(matcher, 4));
            assertEquals(1.5d, reader.nextDoubleValue());
            assertEquals(StreamingReaderV3.Token.NUMBER, reader.currentToken());
        }
    }

    @Test
    void skipAndRawLeaveCompletedContainerCurrent() throws Exception {
        Jackson2NameMatcherV3 matcher = Jackson2NameMatcherV3.of("skip", "raw", "tail");
        try (Jackson2ReaderV3 reader = reader(
                "{\"skip\":{\"nested\":[1]},\"raw\":{\"number\":2},\"tail\":\"done\"}")) {
            reader.startDocument();
            reader.beginObject();
            assertEquals(0, reader.nextObjectField(matcher));
            reader.skipValue();
            assertEquals(StreamingReaderV3.Token.END_OBJECT, reader.currentToken());
            assertEquals(1, reader.nextObjectField(matcher));
            Map<?, ?> raw = (Map<?, ?>) reader.readRawNode();
            assertEquals(2, raw.get("number"));
            assertEquals(StreamingReaderV3.Token.END_OBJECT, reader.currentToken());
            assertEquals(2, reader.nextObjectField(matcher));
            assertEquals("done", reader.readString());
        }
    }

    @Test
    void documentRootAndEndReachEof() throws Exception {
        try (Jackson2ReaderV3 reader = reader("42")) {
            reader.startDocument();
            assertEquals(StreamingReaderV3.Token.NUMBER, reader.currentToken());
            assertEquals(42, reader.readIntValue());
            reader.endDocument();
            assertEquals(StreamingReaderV3.Token.EOF, reader.currentToken());
        }
    }

    @Test
    void streamingIoV3ReadsOrderedAndReorderedPojoFieldsAndSkipsUnknown() throws Exception {
        OrderedPojo ordered = read(
                "{\"first\":1,\"child\":{\"first\":3,\"second\":4},\"second\":2}",
                OrderedPojo.class);
        OrderedPojo reordered = read(
                "{\"second\":2,\"unknown\":{\"nested\":true},\"child\":{\"second\":4,"
                        + "\"unknown\":{\"nested\":[1]},\"first\":3},\"first\":1}",
                OrderedPojo.class);

        assertEquals(1, ordered.first);
        assertEquals(2, ordered.second);
        assertEquals(3, ordered.child.first);
        assertEquals(4, ordered.child.second);
        assertEquals(1, reordered.first);
        assertEquals(2, reordered.second);
        assertEquals(3, reordered.child.first);
        assertEquals(4, reordered.child.second);
    }

    @Test
    void streamingIoV3ReadsAliasesDynamicPropertiesAndContainers() throws Exception {
        DynamicPojo dynamic = read("{\"oldName\":\"value\",\"extra\":[1,true]}", DynamicPojo.class);
        Containers containers = read("{\"map\":{\"a\":1},\"list\":[2,3],\"numbers\":[4,5]}", Containers.class);

        assertEquals("value", dynamic.name);
        assertEquals(List.of(1, true), dynamic.get("extra"));
        assertEquals(1, containers.map.get("a"));
        assertEquals(List.of(2, 3), containers.list);
        assertEquals(5, containers.numbers[1]);
    }

    @Test
    void streamingIoV3ReadsRawNodesAndScalars() throws Exception {
        Map<?, ?> raw = (Map<?, ?>) read("{\"items\":[1,true,null]}", Object.class);

        assertEquals(java.util.Arrays.asList(1, true, null), raw.get("items"));
        assertEquals(42, read("42", Integer.class));
        assertEquals(SampleEnum.SECOND, read("\"SECOND\"", SampleEnum.class));
    }

    @Test
    void rawCodecsRejectWrongContainerType() throws Exception {
        try (Jackson2ReaderV3 reader = reader("[]")) {
            reader.startDocument();
            assertThrows(BindingException.class,
                    () -> StreamingIOV3.readNode(reader, MapValue.class, RuntimeContext.EMPTY));
            assertEquals(StreamingReaderV3.Token.START_ARRAY, reader.currentToken());
        }
        try (Jackson2ReaderV3 reader = reader("{}")) {
            reader.startDocument();
            assertThrows(BindingException.class,
                    () -> StreamingIOV3.readNode(reader, ListValue.class, RuntimeContext.EMPTY));
            assertEquals(StreamingReaderV3.Token.START_OBJECT, reader.currentToken());
        }
    }

    @Test
    void streamingIoV3ReadsCreatorAndCurrentOneOf() throws Exception {
        CreatorPojo creator = read("{\"name\":\"created\",\"after\":3}", CreatorPojo.class);
        PetHolder holder = read("{\"pet\":{\"name\":\"Rex\",\"kind\":\"dog\",\"barks\":true}}", PetHolder.class);

        assertEquals("created", creator.name);
        assertEquals(3, creator.after);
        Dog dog = (Dog) holder.pet;
        assertEquals("Rex", dog.name);
        assertEquals(true, dog.barks);
    }

    private static <T> T read(String json, Class<T> type) throws Exception {
        try (Jackson2ReaderV3 reader = reader(json)) {
            reader.startDocument();
            T value = type.cast(StreamingIOV3.readNode(reader, type, RuntimeContext.EMPTY));
            reader.endDocument();
            return value;
        }
    }

    private static Jackson2ReaderV3 reader(String json) throws IOException {
        return new Jackson2ReaderV3(new JsonFactory().createParser(json));
    }

    static class MatcherPojo {
        public String name;
    }

    static class OrderedPojo {
        public int first;
        public OrderedChild child;
        public int second;
    }

    static class OrderedChild {
        public int first;
        public int second;
    }

    static class DynamicPojo extends JsonObject {
        @NodeProperty(aliases = "oldName") public String name;
    }

    static class Containers {
        public Map<String, Integer> map;
        public List<Integer> list;
        public int[] numbers;
    }

    static class CreatorPojo {
        final String name;
        public int after;

        @NodeCreator
        CreatorPojo(@NodeProperty("name") String name) {
            this.name = name;
        }
    }

    static class PetHolder {
        @OneOf(value = {@OneOf.Mapping(value = Dog.class, when = "dog")}, key = "kind")
        public Animal pet;
    }

    @NodeValue
    static class MapValue {
        final Map<String, Object> value;

        MapValue(Map<String, Object> value) {
            this.value = value;
        }

        @ValueToRaw
        Map<String, Object> encode() {
            return value;
        }

        @RawToValue
        static MapValue decode(Map<String, Object> raw) {
            return new MapValue(raw);
        }
    }

    @NodeValue
    static class ListValue {
        final List<Object> value;

        ListValue(List<Object> value) {
            this.value = value;
        }

        @ValueToRaw
        List<Object> encode() {
            return value;
        }

        @RawToValue
        static ListValue decode(List<Object> raw) {
            return new ListValue(raw);
        }
    }

    static class Animal extends JsonObject {
        public String name;
    }

    static class Dog extends Animal {
        public boolean barks;
    }

    enum SampleEnum { FIRST, SECOND }
}
