package org.sjf4j.backend.jsonp.binding;

import jakarta.json.Json;
import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingReader.Token;

import java.io.StringReader;
import java.math.BigInteger;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonpReaderTest {

    @Test
    void readsPrimitiveValuesAndNormalizesNumbers() throws Exception {
        try (JsonpReader reader = reader("[1,2147483648,123456789012345678901234567890,1.25,\"x\",true,null]")) {
            reader.startArray();
            assertInstanceOf(Integer.class, reader.nextNumber());
            assertInstanceOf(Long.class, reader.nextNumber());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.nextNumber());
            assertEquals(1.25d, reader.nextNumber());
            assertEquals('x', reader.nextCharValue());
            assertTrue(reader.nextBooleanValue());
            reader.nextNull();
            assertTrue(reader.nextIfArrayEnd());
            assertEquals(Token.EOF, reader.peekToken());
        }
    }

    @Test
    void checksNumberRangesAndTokenTypes() throws Exception {
        try (JsonpReader reader = reader("128")) {
            assertThrows(ArithmeticException.class, reader::nextByteValue);
        }
        try (JsonpReader reader = reader("1e50")) {
            assertThrows(ArithmeticException.class, reader::nextFloatValue);
        }
        try (JsonpReader reader = reader("true")) {
            assertThrows(Exception.class, reader::nextString);
        }
        try (JsonpReader reader = reader("1.0")) {
            assertThrows(Exception.class, reader::nextLongValue);
        }
        try (JsonpReader reader = reader("1e2")) {
            assertThrows(Exception.class, reader::nextIntValue);
        }
        try (JsonpReader reader = reader("1.0")) {
            assertThrows(Exception.class, reader::nextShortValue);
        }
        try (JsonpReader reader = reader("1e2")) {
            assertThrows(Exception.class, reader::nextByteValue);
        }
        try (JsonpReader reader = reader("1.0")) {
            assertThrows(Exception.class, reader::nextBigInteger);
        }
        try (JsonpReader reader = reader("[1.0,1e2]")) {
            reader.startArray();
            assertInstanceOf(Double.class, reader.nextNumber());
            assertInstanceOf(Double.class, reader.nextNumber());
            reader.endArray();
        }
    }

    @Test
    void skipsNestedValuesAndRejectsEof() throws Exception {
        try (JsonpReader reader = reader("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]")) {
            reader.startArray();
            reader.skipNext();
            assertEquals("kept", reader.nextString());
            reader.endArray();
        }
        try (JsonpReader reader = reader("null")) {
            reader.nextNull();
            assertEquals(Token.EOF, reader.peekToken());
            assertThrows(Exception.class, reader::skipNext);
        }
    }

    @Test
    void closesWrappedParser() throws Exception {
        jakarta.json.stream.JsonParser parser = Json.createParser(new StringReader("null"));
        JsonpReader reader = new JsonpReader(parser);
        reader.close();
    }

    @Test
    void retainsCurrentEventOfAdvancedParser() throws Exception {
        jakarta.json.stream.JsonParser parser = Json.createParser(new StringReader("[1,2]"));
        parser.next();
        parser.next();

        try (JsonpReader reader = new JsonpReader(parser)) {
            assertEquals(1, reader.nextIntValue());
            assertEquals(2, reader.nextIntValue());
            reader.endArray();
        }
    }

    @Test
    void fallsBackWhenCurrentEventIsUnsupported() throws Exception {
        jakarta.json.stream.JsonParser parser = Json.createParser(new StringReader("1"));
        jakarta.json.stream.JsonParser unsupported = (jakarta.json.stream.JsonParser) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { jakarta.json.stream.JsonParser.class },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("currentEvent")) {
                        throw new UnsupportedOperationException();
                    }
                    return method.invoke(parser, arguments);
                });

        try (JsonpReader reader = new JsonpReader(unsupported)) {
            assertEquals(1, reader.nextIntValue());
            assertEquals(Token.EOF, reader.peekToken());
        }
    }

    private static JsonpReader reader(String json) {
        return new JsonpReader(Json.createParser(new StringReader(json)));
    }
}
