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
import java.util.List;
import java.util.Map;

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
GsonParser reader = reader("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]");
try {
            reader.startArray();
            reader.skipNode();
            assertEquals("kept", reader.readString());
            reader.endArray();
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void preservesNumberRepresentations() throws Exception {
        {
GsonParser reader = reader("[1,2147483648,123456789012345678901234567890,1.25,1e10000]");
try {
            reader.startArray();
            assertInstanceOf(Integer.class, reader.readNumber());
            assertInstanceOf(Long.class, reader.readNumber());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.readNumber());
            assertEquals(1.25d, reader.readNumber());
            assertEquals(new BigDecimal("1e10000"), reader.readNumber());
            reader.endArray();
        
} finally { reader.release(); }
}
    }

    @Test
    void validatesNumericNarrowing() throws Exception {
        {
GsonParser reader = reader("[128,-129,32768,-32769,1e50]");
try {
            reader.startArray();
            assertThrows(Exception.class, reader::readByteValue);
            assertThrows(Exception.class, reader::readByteValue);
            assertThrows(Exception.class, reader::readShortValue);
            assertThrows(Exception.class, reader::readShortValue);
            assertThrows(Exception.class, reader::readFloatValue);
            reader.endArray();
        
} finally { reader.release(); }
}
    }

    @Test
    void readsShortAndFloatValues() throws Exception {
        {
GsonParser reader = reader("[32767,1.25]");
try {
            reader.startArray();
            assertEquals(Short.MAX_VALUE, reader.readShortValue());
            assertEquals(1.25f, reader.readFloatValue());
            reader.endArray();
        
} finally { reader.release(); }
}
    }

    @Test
    void rejectsNullCharWithBindingException() throws Exception {
        {
GsonParser reader = reader("null");
try {
            BindingException error = assertThrows(BindingException.class, reader::readCharValue);
            assertEquals("cannot read null as char", error.getMessage());
        
} finally { reader.release(); }
}
    }

    @Test
    void exposesLogicalTokensAndConditionalConsumption() throws Exception {
        {
GsonParser reader = reader("{\"id\":null}");
try {
            assertEquals(StreamingParser.Token.OBJECT_START, reader.peekToken());
            assertFalse(reader.nextIfArrayStart());
            reader.startObject();
            assertEquals(StreamingParser.Token.NAME, reader.peekToken());
            assertEquals("id", reader.nextName());
            assertEquals(StreamingParser.Token.NULL, reader.peekToken());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfObjectEnd());
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            reader.endRead();
        
} finally { reader.release(); }
}

        {
GsonParser reader = reader("[null]");
try {
            assertTrue(reader.nextIfArrayStart());
            assertTrue(reader.nextIfNull());
            assertFalse(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
        
} finally { reader.release(); }
}
    }

    @Test
    void readsRawNodesWithSjf4jCollectionAndNumberSemantics() throws Exception {
        {
GsonParser reader = reader(
                "{\"text\":\"Ada\",\"number\":7,\"large\":123456789012345678901234567890,"
                        + "\"decimal\":1e10000,\"enabled\":true,\"empty\":null,"
                        + "\"nested\":{\"first\":\"one\"},\"items\":[false,{\"second\":2}]}");
try {

            assertEquals(StreamingParser.Token.OBJECT_START, reader.peekToken());
            Map<?, ?> value = (Map<?, ?>) reader.readRaw();

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
            assertEquals(StreamingParser.Token.EOF, reader.peekToken());
            reader.endRead();
        
} finally { reader.release(); }
}
    }

    @Test
    void rawNodeReportsEofUsingStreamingTokenSemantics() throws Exception {
        {
GsonParser reader = reader("");
try {
            assertThrows(IOException.class, reader::readRaw);
        
} finally { reader.release(); }
}
    }

    @Test
    void rawNodeConsumesOneValue() throws Exception {
        {
GsonParser reader = reader("[{\"id\":7},\"next\"]");
try {
            reader.startArray();
            assertEquals(7, ((Map<?, ?>) reader.readRaw()).get("id"));
            assertEquals(StreamingParser.Token.STRING, reader.peekToken());
            assertEquals("next", reader.readString());
            reader.endArray();
        
} finally { reader.release(); }
}
    }


    @Test
    void preservesGsonLenientScalarConversions() throws Exception {
        {
GsonParser reader = reader(
                "[123,\"456\",\"789\",\"1.25\",\"12.5\"]");
try {

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
            reader.endRead();
        
} finally { reader.release(); }
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



    private static GsonParser reader(String json) {
        return new GsonParser(new JsonReader(new StringReader(json)));
    }
}
