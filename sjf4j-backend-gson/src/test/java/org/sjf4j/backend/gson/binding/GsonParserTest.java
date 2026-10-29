package org.sjf4j.backend.gson.binding;

import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingParser;
import org.sjf4j.exception.BindingException;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GsonParserTest {

    @Test
    void skipsNestedNode() throws Exception {
        {
            GsonParser parser = parser("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]");
            try {
                parser.readArrayStart();
                parser.skipNode();
                assertEquals("kept", parser.readString());
                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void preservesNumberRepresentations() throws Exception {
        {
            GsonParser parser = parser("[1,2147483648,123456789012345678901234567890,1.25,1e10000]");
            try {
                parser.readArrayStart();
                assertInstanceOf(Integer.class, parser.readNumber());
                assertInstanceOf(Long.class, parser.readNumber());
                assertEquals(new BigInteger("123456789012345678901234567890"), parser.readNumber());
                assertEquals(1.25d, parser.readNumber());
                assertEquals(new BigDecimal("1e10000"), parser.readNumber());
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void validatesNumericNarrowing() throws Exception {
        {
            GsonParser parser = parser("[128,-129,32768,-32769,1e50]");
            try {
                parser.readArrayStart();
                assertThrows(Exception.class, parser::readByteValue);
                assertThrows(Exception.class, parser::readByteValue);
                assertThrows(Exception.class, parser::readShortValue);
                assertThrows(Exception.class, parser::readShortValue);
                assertThrows(Exception.class, parser::readFloatValue);
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsShortAndFloatValues() throws Exception {
        {
            GsonParser parser = parser("[32767,1.25]");
            try {
                parser.readArrayStart();
                assertEquals(Short.MAX_VALUE, parser.readShortValue());
                assertEquals(1.25f, parser.readFloatValue());
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void rejectsNullCharWithBindingException() throws Exception {
        {
            GsonParser parser = parser("null");
            try {
                BindingException error = assertThrows(BindingException.class, parser::readCharValue);
                assertEquals("cannot read null as char", error.getMessage());

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void exposesLogicalTokensAndConditionalConsumption() throws Exception {
        {
            GsonParser parser = parser("{\"id\":null}");
            try {
                assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
                assertFalse(parser.nextIfArrayStart());
                parser.readObjectStart();
                assertEquals(StreamingParser.Token.NAME, parser.peekToken());
                assertEquals("id", parser.nextName());
                assertEquals(StreamingParser.Token.NULL, parser.peekToken());
                assertTrue(parser.nextIfNull());
                assertTrue(parser.nextIfObjectEnd());
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.close();
            }
        }

        {
            GsonParser parser = parser("[null]");
            try {
                assertTrue(parser.nextIfArrayStart());
                assertTrue(parser.nextIfNull());
                assertFalse(parser.nextIfNull());
                assertTrue(parser.nextIfArrayEnd());

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void readsRawNodesWithSjf4jCollectionAndNumberSemantics() throws Exception {
        {
            GsonParser parser = parser(
                "{\"text\":\"Ada\",\"number\":7,\"large\":123456789012345678901234567890,"
                        + "\"decimal\":1e10000,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}");
            try {

                assertEquals(StreamingParser.Token.OBJECT_START, parser.peekToken());
                Map<?, ?> value = (Map<?, ?>) parser.readRaw();

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
                assertEquals(StreamingParser.Token.EOF, parser.peekToken());
                parser.end();

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void rawNodeReportsEofUsingStreamingTokenSemantics() throws Exception {
        {
            GsonParser parser = parser("");
            try {
                assertThrows(IOException.class, parser::readRaw);

            } finally {
                parser.close();
            }
        }
    }

    @Test
    void rawNodeConsumesOneValue() throws Exception {
        {
            GsonParser parser = parser("[{\"id\":7},\"next\"]");
            try {
                parser.readArrayStart();
                assertEquals(7, ((Map<?, ?>) parser.readRaw()).get("id"));
                assertEquals(StreamingParser.Token.STRING, parser.peekToken());
                assertEquals("next", parser.readString());
                parser.readArrayEnd();

            } finally {
                parser.close();
            }
        }
    }


    @Test
    void preservesGsonLenientScalarConversions() throws Exception {
        {
            GsonParser parser = parser(
                "[123,\"456\",\"789\",\"1.25\",\"12.5\"]");
            try {

                parser.readArrayStart();

                // NUMBER -> String
                assertEquals("123", parser.readString());

                // STRING -> numeric types
                assertEquals(456, parser.readIntValue());
                assertEquals(new BigInteger("789"),
                        parser.readBigInteger());
                assertEquals(new BigDecimal("1.25"),
                        parser.readBigDecimal());
                assertEquals(12.5d,
                        parser.readNumber().doubleValue());

                parser.readArrayEnd();
                parser.end();

            } finally {
                parser.close();
            }
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



    @Test
    void rawCollectionsPreserveValuesAcrossSizes() throws Exception {
        for (int size = 0; size <= 8; size++) {
            StringBuilder objectJson = new StringBuilder("{");
            StringBuilder arrayJson = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    objectJson.append(',');
                    arrayJson.append(',');
                }
                objectJson.append('"').append("k").append(i).append("\":").append(i);
                arrayJson.append(i);
            }
            objectJson.append('}');
            arrayJson.append(']');

            try (GsonParser p = parser(objectJson.toString())) {
                Map<String, Object> result = p.readRawObject();
                assertEquals(LinkedHashMap.class, result.getClass());
                assertEquals(size, result.size());
                for (int i = 0; i < size; i++) {
                    assertEquals("k" + i, new ArrayList<>(result.keySet()).get(i));
                    assertEquals(i, ((Number) result.get("k" + i)).intValue());
                }
                p.end();
            }
            try (GsonParser p = parser(arrayJson.toString())) {
                List<Object> result = p.readRawArray();
                assertEquals(ArrayList.class, result.getClass());
                assertEquals(size, result.size());
                for (int i = 0; i < size; i++) {
                    assertEquals(i, ((Number) result.get(i)).intValue());
                }
                p.end();
            }
        }

        try (GsonParser p = parser("{\"a\":1,\"b\":2,\"c\":3,\"a\":4,\"d\":[null,{\"ok\":true}]}")) {
            Map<String, Object> result = p.readRawObject();
            assertEquals(Arrays.asList("a", "b", "c", "d"),
                    new ArrayList<>(result.keySet()));
            assertEquals(4, ((Number) result.get("a")).intValue());
            assertEquals(2, ((List<?>) result.get("d")).size());
            p.end();
        }
    }

    private static GsonParser parser(String json) {
        return new GsonParser(new JsonReader(new StringReader(json)));
    }
}
