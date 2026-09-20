package org.sjf4j.backend.gson.binding;

import com.google.gson.stream.JsonReader;
import org.sjf4j.binding.StreamingReader.Token;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GsonReaderTest {

    @Test
    void skipsNestedValue() throws Exception {
        try (GsonReader reader = reader("[{\"discard\":[1,{\"nested\":true},null]},\"kept\"]")) {
            reader.startArray();
            reader.skipNext();
            assertEquals("kept", reader.nextString());
            reader.endArray();
        }
    }

    @Test
    void preservesNumberRepresentations() throws Exception {
        try (GsonReader reader = reader("[1,2147483648,123456789012345678901234567890,1.25]")) {
            reader.startArray();
            assertInstanceOf(Integer.class, reader.nextNumber());
            assertInstanceOf(Long.class, reader.nextNumber());
            assertEquals(new BigInteger("123456789012345678901234567890"), reader.nextNumber());
            assertEquals(1.25d, reader.nextNumber());
            reader.endArray();
        }
    }

    @Test
    void rejectsOutOfRangeByteAndShortValues() throws Exception {
        try (GsonReader reader = reader("[128,-129,32768,-32769]")) {
            reader.startArray();
            assertThrows(Exception.class, reader::nextByteValue);
            assertThrows(Exception.class, reader::nextByteValue);
            assertThrows(Exception.class, reader::nextShortValue);
            assertThrows(Exception.class, reader::nextShortValue);
            reader.endArray();
        }
    }

    @Test
    void rejectsNonFiniteFloat() throws Exception {
        try (GsonReader reader = reader("1e50")) {
            assertThrows(Exception.class, reader::nextFloatValue);
        }
    }

    @Test
    void readsShortAndFloatValues() throws Exception {
        try (GsonReader reader = reader("[32767,1.25]")) {
            reader.startArray();
            assertEquals(Short.MAX_VALUE, reader.nextShortValue());
            assertEquals(1.25f, reader.nextFloatValue());
            reader.endArray();
        }
    }

    @Test
    void conditionallyConsumesNativeTokens() throws Exception {
        try (GsonReader reader = reader("[null]")) {
            reader.startArray();
            assertTrue(reader.nextIfNull());
            assertFalse(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
        }
        try (GsonReader reader = reader("{}")) {
            reader.startObject();
            assertTrue(reader.nextIfObjectEnd());
        }
    }

    @Test
    void refreshesPeekedTokenAfterConsumption() throws Exception {
        try (GsonReader reader = reader("1")) {
            assertEquals(Token.NUMBER, reader.peekToken());
            assertEquals(1, reader.nextIntValue());
            assertEquals(Token.EOF, reader.peekToken());
        }
    }

    private static GsonReader reader(String json) {
        return new GsonReader(new JsonReader(new StringReader(json)));
    }
}
