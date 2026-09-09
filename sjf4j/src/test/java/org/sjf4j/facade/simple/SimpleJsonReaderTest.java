package org.sjf4j.facade.simple;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.BindingException;
import org.sjf4j.facade.StreamingReader;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleJsonReaderTest {

    @Test
    void testTokenAndScalarReads() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(" { \"name\":\"A\\nB\", \"n\":123, \"b\": true, \"nil\": null } "))) {
            assertEquals(StreamingReader.Token.START_OBJECT, reader.peekToken());
            assertEquals(StreamingReader.Token.START_OBJECT, reader.peekToken());
            reader.startObject();
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("name", reader.nextName());
            assertEquals("A\nB", reader.nextString());
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("n", reader.nextName());
            assertEquals(123, reader.nextNumber().intValue());
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("b", reader.nextName());
            assertEquals(StreamingReader.Token.BOOLEAN, reader.peekToken());
            assertEquals(Boolean.TRUE, reader.nextBoolean());
            assertEquals(StreamingReader.Token.FIELD_NAME, reader.peekToken());
            assertEquals("nil", reader.nextName());
            assertEquals(StreamingReader.Token.NULL, reader.peekToken());
            reader.nextNull();
            reader.endObject();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        }
    }

    @Test
    void testPeekTokenAtEmptyInputAndUnknownCharacter() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(""))) {
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("x"))) {
            assertEquals(StreamingReader.Token.UNKNOWN, reader.peekToken());
        }
    }

    @Test
    void testNumericReaders() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[1,2,3,4,5.5,6.5,7,8]"))) {
            reader.startArray();
            assertEquals(1L, reader.nextLong());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(2, reader.nextInt());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals((short) 3, reader.nextShort());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals((byte) 4, reader.nextByte());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(5.5d, reader.nextDouble());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(6.5f, reader.nextFloat());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(new BigInteger("7"), reader.nextBigInteger());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(new BigDecimal("8"), reader.nextBigDecimal());
            reader.endArray();
        }
    }

    @Test
    void testNumericReadersWithLargeAndExponentValues() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[-9223372036854775808,2147483648,1e3,9999999999999999999]"))) {
            reader.startArray();
            assertEquals(Long.MIN_VALUE, reader.nextLong());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(2147483648L, reader.nextLong());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(1000.0d, reader.nextDouble());
            assertEquals(StreamingReader.Token.NUMBER, reader.peekToken());
            assertEquals(new BigInteger("9999999999999999999"), reader.nextBigInteger());
            reader.endArray();
        }
    }

    @Test
    void directReadsRequireContainerDelimiters() throws Exception {
        assertDirectReadFails("[1,,2]", reader -> { reader.startArray(); reader.nextInt(); reader.nextInt(); });
        assertDirectReadFails("[1 2]", reader -> { reader.startArray(); reader.nextInt(); reader.nextInt(); });
        assertDirectReadFails("[1,]", reader -> { reader.startArray(); reader.nextInt(); reader.endArray(); });
        assertDirectReadFails("[:1]", reader -> { reader.startArray(); reader.peekToken(); });
        assertDirectReadFails("[1}", reader -> { reader.startArray(); reader.nextInt(); reader.endArray(); });
        assertDirectReadFails("{\"a\":1 \"b\":2}", reader -> { reader.startObject(); reader.nextName(); reader.nextInt(); reader.nextName(); });
        assertDirectReadFails("{\"a\" 1}", reader -> { reader.startObject(); reader.nextName(); });
        assertDirectReadFails("{\"a\":1,}", reader -> { reader.startObject(); reader.nextName(); reader.nextInt(); reader.endObject(); });
        assertDirectReadFails("{\"a\":1]", reader -> { reader.startObject(); reader.nextName(); reader.nextInt(); reader.endObject(); });

        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(" [ { \"a\" : [ true , null ] } ] "))) {
            reader.startArray();
            reader.startObject();
            assertEquals("a", reader.nextName());
            reader.startArray();
            assertEquals(Boolean.TRUE, reader.nextBoolean());
            reader.nextNull();
            assertEquals(true, reader.nextIfArrayEnd());
            assertEquals(true, reader.nextIfObjectEnd());
            assertEquals(true, reader.nextIfArrayEnd());
            reader.endDocument();
        }
    }

    @Test
    void directNumbersUseJsonGrammar() throws Exception {
        for (String json : new String[]{"01", "1.", "-", "1e", "1e+", "-.1"}) {
            assertDirectReadFails(json, SimpleJsonReader::nextNumber);
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[-0,0,12.34e-5]"))) {
            reader.startArray();
            assertEquals(0, reader.nextInt());
            assertEquals(0, reader.nextInt());
            assertEquals(12.34e-5, reader.nextDouble());
            reader.endArray();
        }
    }

    @Test
    void testEscapesAndSurrogatePairs() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\\uD83D\\uDE00\""))) {
            assertEquals("\"\\/\b\f\n\r\tA😀", reader.nextString());
        }
    }

    @Test
    void strictSkippedStringsValidateSurrogatesAndStructure() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"\\uD83D\\uDE00\""))) {
            reader.skipNext();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
        }
        for (String json : new String[]{"\"\\uD83D\"", "\"\\uD83D\\u0041\"", "\"\\uDE00\"",
                "[1,,2]", "{\"a\":{\"b\":1 \"c\":2}}", "{\"" + (char) 1 + "\":1}"}) {
            assertSkipFails(json);
        }
        for (String json : new String[]{"-0", "0", "-12.34e+56", "1E-2"}) {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(json))) {
                reader.skipNext();
                assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            }
        }
    }

    @Test
    void directStringsAndNamesValidateSurrogates() throws Exception {
        String valid = "\\uD83D\\uDE00";
        String[] invalid = {"\\uD83D", "\\uDE00", "\\uD83D\\u0041"};
        for (String escape : invalid) {
            assertStringFails("\"" + escape + "\"");
            assertNameFails("{\"" + escape + "\":null}");
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"" + valid + "\""))) {
            assertEquals("😀", reader.nextString());
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"" + valid + "\":null}"))) {
            reader.startObject();
            assertEquals("😀", reader.nextName());
            reader.nextNull();
            reader.endObject();
        }
    }

    @Test
    void unterminatedDirectStringCannotCompleteDocument() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"abc"))) {
            assertThrows(BindingException.class, reader::nextString);
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"abc"))) {
            assertThrows(BindingException.class, () -> {
                reader.nextString();
                reader.endDocument();
            });
        }
    }

    @Test
    void unterminatedContainersCannotCompleteDocument() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("["))) {
            reader.startArray();
            assertThrows(Exception.class, reader::endDocument);
        }
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[1"))) {
            reader.startArray();
            reader.nextInt();
            assertThrows(Exception.class, reader::endDocument);
        }
    }

    @Test
    void testSkipNextAcrossValueKinds() throws Exception {
        try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[\"x\",123,true,false,null,{\"a\":[1]},[2,3]]"))) {
            reader.startArray();
            reader.skipNext();
            reader.peekToken();
            reader.skipNext();
            reader.peekToken();
            reader.skipNext();
            reader.peekToken();
            reader.skipNext();
            reader.peekToken();
            reader.skipNext();
            reader.peekToken();
            reader.skipNext();
            reader.peekToken();
            reader.skipNext();
            reader.endArray();
        }
    }

    @Test
    void testErrorPaths() {
        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("x"))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("["))) {
                reader.endArray();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{"))) {
                reader.endObject();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"\\x\""))) {
                reader.nextString();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"\\u00XZ\""))) {
                reader.nextString();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"\\uD83D\\u0041\""))) {
                reader.nextString();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("truX"))) {
                reader.nextBoolean();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("nulX"))) {
                reader.nextNull();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{a:1}"))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[1 2]"))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"abc"))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("\"\\u12XZ\""))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\" 1}"))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":1]"))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("[1}"))) {
                reader.skipNext();
            }
        });

        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(" t"))) {
                reader.nextBoolean();
            }
        });
    }

    @Test
    void testErrorPathsIncludeResolvedPath() {
        BindingException missingColon = assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\" 1}"))) {
                reader.skipNext();
            }
        });
        assertEquals("$.a", missingColon.getPathSegment().rootedPathExpr());

        BindingException badArrayElement = assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader("{\"a\":[1,}]}"))) {
                reader.skipNext();
            }
        });
        assertEquals("$.a[1]", badArrayElement.getPathSegment().rootedPathExpr());
    }

    private static void assertSkipFails(String json) {
        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(json))) {
                reader.skipNext();
            }
        }, json);
    }

    private static void assertStringFails(String json) {
        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(json))) {
                reader.nextString();
            }
        }, json);
    }

    private static void assertNameFails(String json) {
        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(json))) {
                reader.startObject();
                reader.nextName();
            }
        }, json);
    }

    @FunctionalInterface
    private interface ReaderAction {
        void read(SimpleJsonReader reader) throws Exception;
    }

    private static void assertDirectReadFails(String json, ReaderAction action) {
        assertThrows(BindingException.class, () -> {
            try (SimpleJsonReader reader = new SimpleJsonReader(new StringReader(json))) {
                action.read(reader);
            }
        }, json);
    }
}
