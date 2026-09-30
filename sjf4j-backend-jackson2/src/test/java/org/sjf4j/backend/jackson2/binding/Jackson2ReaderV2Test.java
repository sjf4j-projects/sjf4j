package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingReaderV2;
import org.sjf4j.binding.StreamingReaderV2.Token;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Jackson2ReaderV2Test {

    @Test
    void readsDocumentRootAndReachesEof() throws Exception {
        try (Jackson2ReaderV2 reader = reader("42")) {
            reader.startDocument();

            assertEquals(Token.NUMBER, reader.currentToken());
            assertEquals(42, reader.readIntValue());
            assertEquals(Token.NUMBER, reader.currentToken());

            reader.endDocument();
            assertEquals(Token.EOF, reader.currentToken());
        }
    }

    @Test
    void matchesOrderedJacksonNamesAndFallsBackForMismatchesAndUnknownNames() throws Exception {
        StreamingReaderV2.NameMatcher matcher =
                Jackson2ReaderV2.createNameMatcher("first", "second");

        try (Jackson2ReaderV2 reader = reader("{\"first\":1,\"second\":2,\"other\":3}")) {
            reader.startDocument();
            reader.beginObject();

            assertEquals(0, reader.nextObjectField(matcher, 0));
            assertEquals(1, reader.readIntValue());

            assertEquals(1, reader.nextObjectField(matcher, 0));
            assertEquals(2, reader.readIntValue());

            assertEquals(StreamingReaderV2.UNKNOWN_FIELD, reader.nextObjectField(matcher));
            assertEquals(3, reader.readIntValue());
            assertEquals(StreamingReaderV2.END_OF_OBJECT, reader.nextObjectField(matcher));
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void matchesGenericNameMatchers() throws Exception {
        StreamingReaderV2.NameMatcher matcher = new StreamingReaderV2.NameMatcher() {
            @Override
            public String name(int index) {
                return index == 0 ? "first" : "second";
            }

            @Override
            public int match(String name) {
                return "second".equals(name) ? 1 : UNKNOWN;
            }
        };

        try (Jackson2ReaderV2 reader = reader("{\"second\":2}")) {
            reader.startDocument();
            reader.beginObject();

            assertEquals(1, reader.nextObjectField(matcher, 0));
            assertEquals(2, reader.readIntValue());
            assertEquals(StreamingReaderV2.END_OF_OBJECT, reader.nextObjectField(matcher));
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void orderedExpectedNameLeavesFieldForDirectValueConsumption() throws Exception {
        StreamingReaderV2.NameMatcher matcher = Jackson2ReaderV2.createNameMatcher("first");
        try (JsonParser parser = new JsonFactory().createParser("{\"first\":7}");
             Jackson2ReaderV2 reader = new Jackson2ReaderV2(parser)) {
            reader.startDocument();
            reader.beginObject();

            assertEquals(true, reader.nextExpectedObjectField(matcher, 0));
            assertEquals(JsonToken.FIELD_NAME, parser.currentToken());
            assertEquals(7, reader.nextIntValue());
            assertEquals(Token.NUMBER, reader.currentToken());
        }
    }

    @Test
    void orderedExpectedNameMismatchPreservesFieldOrObjectEndForFallback() throws Exception {
        StreamingReaderV2.NameMatcher matcher = Jackson2ReaderV2.createNameMatcher("first", "second");
        try (JsonParser parser = new JsonFactory().createParser("{\"second\":2}");
             Jackson2ReaderV2 reader = new Jackson2ReaderV2(parser)) {
            reader.startDocument();
            reader.beginObject();

            assertEquals(false, reader.nextExpectedObjectField(matcher, 0));
            assertEquals(JsonToken.FIELD_NAME, parser.currentToken());
            assertEquals(1, reader.matchCurrentObjectField(matcher));
            assertEquals(2, reader.readIntValue());
            assertEquals(false, reader.nextExpectedObjectField(matcher, 0));
            assertEquals(JsonToken.END_OBJECT, parser.currentToken());
            assertEquals(StreamingReaderV2.END_OF_OBJECT, reader.matchCurrentObjectField(matcher));
        }
    }

    @Test
    void currentObjectNameMatchDoesNotAdvanceForKnownUnknownOrEnd() throws Exception {
        StreamingReaderV2.NameMatcher matcher = Jackson2ReaderV2.createNameMatcher("known");
        try (JsonParser parser = new JsonFactory().createParser("{\"known\":1,\"other\":2}");
             Jackson2ReaderV2 reader = new Jackson2ReaderV2(parser)) {
            reader.startDocument();
            reader.beginObject();

            assertEquals(true, reader.nextOrderedObjectField());
            assertEquals(0, reader.matchCurrentObjectName(matcher));
            assertEquals(JsonToken.FIELD_NAME, parser.currentToken());
            assertEquals(1, reader.nextIntValue());

            assertEquals(true, reader.nextOrderedObjectField());
            assertEquals(StreamingReaderV2.UNKNOWN_FIELD, reader.matchCurrentObjectName(matcher));
            assertEquals(JsonToken.FIELD_NAME, parser.currentToken());
            reader.nextObjectValue();
            assertEquals(2, reader.readIntValue());

            assertEquals(false, reader.nextOrderedObjectField());
            assertEquals(StreamingReaderV2.END_OF_OBJECT, reader.matchCurrentObjectName(matcher));
            assertEquals(JsonToken.END_OBJECT, parser.currentToken());
        }
    }

    @Test
    void orderedScalarConsumersUseFusedDefaultsAndLeaveTheirValueCurrent() throws Exception {
        StreamingReaderV2.NameMatcher matcher = Jackson2ReaderV2.createNameMatcher(
                "long", "int", "boolean", "string", "double");
        try (Jackson2ReaderV2 reader = reader(
                "{\"long\":null,\"int\":null,\"boolean\":null,\"string\":null,\"double\":1.5}")) {
            reader.startDocument();
            reader.beginObject();
            assertEquals(true, reader.nextExpectedObjectField(matcher, 0));
            assertEquals(0L, reader.nextLongValue());
            assertEquals(Token.NULL, reader.currentToken());
            assertEquals(true, reader.nextExpectedObjectField(matcher, 1));
            assertEquals(0, reader.nextIntValue());
            assertEquals(Token.NULL, reader.currentToken());
            assertEquals(true, reader.nextExpectedObjectField(matcher, 2));
            assertEquals(false, reader.nextBooleanValue());
            assertEquals(Token.NULL, reader.currentToken());
            assertEquals(true, reader.nextExpectedObjectField(matcher, 3));
            assertNull(reader.nextStringOrNull());
            assertEquals(Token.NULL, reader.currentToken());
            assertEquals(true, reader.nextExpectedObjectField(matcher, 4));
            assertEquals(1.5d, reader.nextDoubleValue());
            assertEquals(Token.NUMBER, reader.currentToken());
        }
    }

    @Test
    void orderedValueAdvanceLeavesContainerStartCurrent() throws Exception {
        StreamingReaderV2.NameMatcher matcher = Jackson2ReaderV2.createNameMatcher("child");
        try (Jackson2ReaderV2 reader = reader("{\"child\":[]}")) {
            reader.startDocument();
            reader.beginObject();
            assertEquals(true, reader.nextExpectedObjectField(matcher, 0));

            reader.nextObjectValue();
            assertEquals(Token.START_ARRAY, reader.currentToken());
            reader.beginArray();
            assertEquals(false, reader.nextArrayElement());
            reader.endArray();
        }
    }

    @Test
    void continuesParentObjectAfterNestedObject() throws Exception {
        try (Jackson2ReaderV2 reader = reader("{\"child\":{\"value\":1},\"after\":2}")) {
            reader.startDocument();
            reader.beginObject();
            assertEquals("child", reader.nextObjectField());
            reader.beginObject();
            assertEquals("value", reader.nextObjectField());
            assertEquals(1, reader.readIntValue());
            assertNull(reader.nextObjectField());
            reader.endObject();

            assertEquals("after", reader.nextObjectField());
            assertEquals(2, reader.readIntValue());
            assertNull(reader.nextObjectField());
            reader.endObject();
            reader.endDocument();
        }
    }

    @Test
    void traversesArraysWithNestedValues() throws Exception {
        try (Jackson2ReaderV2 reader = reader("[1,[\"two\",null],{\"enabled\":true}]")) {
            reader.startDocument();
            reader.beginArray();
            assertEquals(true, reader.nextArrayElement());
            assertEquals(1, reader.readIntValue());

            assertEquals(true, reader.nextArrayElement());
            reader.beginArray();
            assertEquals(true, reader.nextArrayElement());
            assertEquals("two", reader.readString());
            assertEquals(true, reader.nextArrayElement());
            reader.readNull();
            assertEquals(false, reader.nextArrayElement());
            reader.endArray();

            assertEquals(true, reader.nextArrayElement());
            reader.beginObject();
            assertEquals("enabled", reader.nextObjectField());
            assertEquals(true, reader.readBooleanValue());
            assertNull(reader.nextObjectField());
            reader.endObject();
            assertEquals(false, reader.nextArrayElement());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void scalarReadsAndNullDoNotAdvance() throws Exception {
        try (Jackson2ReaderV2 reader = reader("[\"text\",7,true,null]")) {
            reader.startDocument();
            reader.beginArray();

            assertEquals(true, reader.nextArrayElement());
            assertEquals("text", reader.readString());
            assertEquals(Token.STRING, reader.currentToken());
            assertEquals(true, reader.nextArrayElement());

            assertEquals(7, reader.readIntValue());
            assertEquals(Token.NUMBER, reader.currentToken());
            assertEquals(true, reader.nextArrayElement());

            assertEquals(true, reader.readBooleanValue());
            assertEquals(Token.BOOLEAN, reader.currentToken());
            assertEquals(true, reader.nextArrayElement());

            assertEquals(Token.NULL, reader.currentToken());
            reader.readNull();
            assertNull(reader.readStringOrNull());
            assertEquals(Token.NULL, reader.currentToken());
            assertEquals(false, reader.nextArrayElement());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void readStringOrNullRequiresAStringForNonNullValues() throws Exception {
        try (Jackson2ReaderV2 reader = reader("[\"text\",7]")) {
            reader.startDocument();
            reader.beginArray();

            assertEquals(true, reader.nextArrayElement());
            assertEquals("text", reader.readStringOrNull());
            assertEquals(Token.STRING, reader.currentToken());

            assertEquals(true, reader.nextArrayElement());
            assertThrows(IOException.class, reader::readStringOrNull);
            assertEquals(Token.NUMBER, reader.currentToken());
        }
    }

    @Test
    void skipValueAndRawNodeLeaveContainerEndTokensCurrent() throws Exception {
        try (Jackson2ReaderV2 reader = reader(
                "{\"skip\":{\"nested\":[1]},\"raw\":{\"number\":2},\"tail\":\"done\"}")) {
            reader.startDocument();
            reader.beginObject();

            assertEquals("skip", reader.nextObjectField());
            reader.skipValue();
            assertEquals(Token.END_OBJECT, reader.currentToken());

            assertEquals("raw", reader.nextObjectField());
            Map<?, ?> raw = (Map<?, ?>) reader.readRawNode();
            assertEquals(2, raw.get("number"));
            assertEquals(Token.END_OBJECT, reader.currentToken());

            assertEquals("tail", reader.nextObjectField());
            assertEquals("done", reader.readString());
            assertNull(reader.nextObjectField());
            reader.endObject();
            reader.endDocument();
        }
    }

    private static Jackson2ReaderV2 reader(String json) throws IOException {
        return new Jackson2ReaderV2(new JsonFactory().createParser(json));
    }
}
