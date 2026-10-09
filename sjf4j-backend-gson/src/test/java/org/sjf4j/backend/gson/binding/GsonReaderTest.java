package org.sjf4j.backend.gson.binding;

import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingReader;
import org.sjf4j.exception.BindingException;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GsonReaderTest {

    @Test
    void skipsNestedNode() throws Exception {
        try (GsonReader reader = reader("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]")) {
            reader.startArray();
            reader.skipNode();
            assertEquals("kept", reader.readString());
            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void preservesNumberRepresentations() throws Exception {
        try (GsonReader reader = reader("[1,2147483648,123456789012345678901234567890,1.25,1e10000]")) {
            reader.startArray();
            assertInstanceOf(Integer.class, reader.readNumber());
            assertInstanceOf(Long.class, reader.readNumber());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.readNumber());
            assertEquals(1.25d, reader.readNumber());
            assertEquals(new BigDecimal("1e10000"), reader.readNumber());
            reader.endArray();
        }
    }

    @Test
    void validatesNumericNarrowing() throws Exception {
        try (GsonReader reader = reader("[128,-129,32768,-32769,1e50]")) {
            reader.startArray();
            assertThrows(Exception.class, reader::readByteValue);
            assertThrows(Exception.class, reader::readByteValue);
            assertThrows(Exception.class, reader::readShortValue);
            assertThrows(Exception.class, reader::readShortValue);
            assertThrows(Exception.class, reader::readFloatValue);
            reader.endArray();
        }
    }

    @Test
    void readsShortAndFloatValues() throws Exception {
        try (GsonReader reader = reader("[32767,1.25]")) {
            reader.startArray();
            assertEquals(Short.MAX_VALUE, reader.readShortValue());
            assertEquals(1.25f, reader.readFloatValue());
            reader.endArray();
        }
    }

    @Test
    void rejectsNullCharWithBindingException() throws Exception {
        try (GsonReader reader = reader("null")) {
            BindingException error = assertThrows(BindingException.class, reader::readCharValue);
            assertEquals("cannot read null as char", error.getMessage());
        }
    }

    @Test
    void exposesLogicalTokensAndConditionalConsumption() throws Exception {
        try (GsonReader reader = reader("{\"id\":null}")) {
            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            assertFalse(reader.nextIfArrayStart());
            reader.startObject();
            assertEquals(StreamingReader.Token.NAME, reader.peekToken());
            assertEquals("id", reader.nextName());
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfObjectEnd());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }

        try (GsonReader reader = reader("[null]")) {
            assertTrue(reader.nextIfArrayStart());
            assertTrue(reader.nextIfNull());
            assertFalse(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
        }
    }

    @Test
    void readsRawNodesWithSjf4jCollectionAndNumberSemantics() throws Exception {
        try (GsonReader reader = reader(
                "{\"text\":\"Ada\",\"number\":7,\"large\":123456789012345678901234567890,"
                        + "\"decimal\":1e10000,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}")) {

            assertEquals(StreamingReader.Token.OBJECT_START, reader.peekToken());
            Map<?, ?> value = (Map<?, ?>) reader.readRawNode();

            assertEquals(LinkedHashMap.class, value.getClass());
            assertEquals(Arrays.asList("text", "number", "large", "decimal", "enabled", "empty", "nested", "items"),
                    Arrays.asList(value.keySet().toArray()));
            assertEquals(7, value.get("number"));
            assertEquals(new BigInteger("123456789012345678901234567890"), value.get("large"));
            assertEquals(new BigDecimal("1e10000"), value.get("decimal"));
            assertEquals(true, value.get("enabled"));
            assertNull(value.get("empty"));
            assertEquals(LinkedHashMap.class, value.get("nested").getClass());
            assertEquals(ArrayList.class, value.get("items").getClass());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            reader.endDocument();
        }
    }

    @Test
    void rawNodeReportsEofUsingStreamingTokenSemantics() throws Exception {
        try (GsonReader reader = reader("")) {
            BindingException error = assertThrows(BindingException.class, reader::readRawNode);
            assertEquals("unexpected token 'EOF'", error.getMessage());
        }
    }

    @Test
    void rawNodeConsumesOneValue() throws Exception {
        try (GsonReader reader = reader("[{\"id\":7},\"next\"]")) {
            reader.startArray();
            assertEquals(7, ((Map<?, ?>) reader.readRawNode()).get("id"));
            assertEquals(StreamingReader.Token.STRING, reader.peekToken());
            assertEquals("next", reader.readString());
            reader.endArray();
        }
    }


    @Test
    void preservesGsonLenientScalarConversions() throws Exception {
        try (GsonReader reader = reader(
                "[123,\"456\",\"789\",\"1.25\",\"12.5\"]")) {

            reader.startArray();

            // NUMBER -> String
            assertEquals("123", reader.readString());

            // STRING -> numeric types
            assertEquals(456, reader.readIntValue());
            assertEquals(new BigInteger("789"),
                    reader.readBigInteger());
            assertEquals(new BigDecimal("1.25"),
                    reader.readBigDecimal());
            assertEquals(12.5d,
                    reader.readNumber().doubleValue());

            reader.endArray();
            reader.endDocument();
        }
    }

    @Test
    void rejectsTruncatedJson() {
        GsonBinder binder = new GsonBinder();

        assertThrows(BindingException.class, () ->
                binder.readNode("{\"name\":", Object.class));

        assertThrows(BindingException.class, () ->
                binder.readNode("[1,", Object.class));

        assertThrows(BindingException.class, () ->
                binder.readNode("{\"name\":\"value\"", Object.class));
    }



    private static GsonReader reader(String json) {
        return new GsonReader(new JsonReader(new StringReader(json)));
    }
}
