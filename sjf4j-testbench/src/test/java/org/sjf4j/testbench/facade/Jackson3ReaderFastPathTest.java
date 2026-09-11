package org.sjf4j.testbench.facade;

import org.junit.jupiter.api.Test;
import org.sjf4j.facade.StreamingReader;
import org.sjf4j.facade.jackson3.Jackson3JsonFacade;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson3ReaderFastPathTest {

    @Test
    void probesAndPrimitiveReadsConsumeTheCurrentToken() throws IOException {
        Jackson3JsonFacade facade = new Jackson3JsonFacade();
        try (StreamingReader reader = facade.createReader("[1,2,3,4,5.5,6.5,true,null]")) {
            assertEquals(StreamingReader.Token.START_ARRAY, reader.peekToken());
            reader.startArray();
            assertFalse(reader.nextIfNull());
            assertEquals(1L, reader.nextLongValue());
            assertEquals(2, reader.nextIntValue());
            assertEquals((short) 3, reader.nextShortValue());
            assertEquals((byte) 4, reader.nextByteValue());
            assertEquals(5.5D, reader.nextDoubleValue());
            assertEquals(6.5F, reader.nextFloatValue());
            assertTrue(reader.nextBooleanValue());
            assertTrue(reader.nextIfNull());
            assertTrue(reader.nextIfArrayEnd());
        }
        try (StreamingReader reader = facade.createReader("{}")) {
            reader.peekToken();
            reader.startObject();
            assertTrue(reader.nextIfObjectEnd());
        }
    }

    @Test
    void primitiveReadsReportWrongTokens() throws IOException {
        Jackson3JsonFacade facade = new Jackson3JsonFacade();
        try (StreamingReader reader = facade.createReader("[null,\"wrong\"]")) {
            reader.peekToken();
            reader.startArray();
            assertThrows(Exception.class, reader::nextIntValue);
        }
    }

    @Test
    void eofDoesNotCauseNpe() throws IOException {
        try (StreamingReader reader = new Jackson3JsonFacade().createReader("")) {
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            assertFalse(reader.nextIfNull());
            assertFalse(reader.nextIfObjectEnd());
            assertFalse(reader.nextIfArrayEnd());
            assertThrows(Exception.class, reader::nextLongValue);
            assertThrows(Exception.class, reader::nextIntValue);
            assertThrows(Exception.class, reader::nextShortValue);
            assertThrows(Exception.class, reader::nextByteValue);
            assertThrows(Exception.class, reader::nextDoubleValue);
            assertThrows(Exception.class, reader::nextFloatValue);
            assertThrows(Exception.class, reader::nextBooleanValue);
        }
    }

    @Test
    void documentContractAndFreshSkip() throws IOException {
        Jackson3JsonFacade facade = new Jackson3JsonFacade();
        try (StreamingReader reader = facade.createReader("[1,{\"a\":[true]}] \n")) {
            reader.startDocument();
            reader.skipNext();
            reader.endDocument();
        }
        for (String json : new String[]{"1 2", "1 garbage"}) {
            try (StreamingReader reader = facade.createReader(json)) {
                reader.startDocument();
                assertThrows(Exception.class, () -> {
                    reader.skipNext();
                    reader.endDocument();
                }, json);
            }
        }
        try (StreamingReader reader = facade.createReader("1")) {
            reader.startDocument();
            assertThrows(Exception.class, reader::endDocument);
            reader.skipNext();
            assertEquals(StreamingReader.Token.EOF, reader.peekToken());
            assertThrows(Exception.class, reader::skipNext);
        }
    }

    @Test
    void forkedRootValuesLeaveTheSourceAtDocumentEnd() throws IOException {
        Jackson3JsonFacade facade = new Jackson3JsonFacade();
        for (String json : new String[]{"1", "[1]", "{\"value\":1}"}) {
            try (StreamingReader reader = facade.createReader(json)) {
                assertEquals(json.charAt(0) == '[' ? StreamingReader.Token.START_ARRAY
                        : json.charAt(0) == '{' ? StreamingReader.Token.START_OBJECT : StreamingReader.Token.NUMBER,
                        reader.peekToken());
                try (StreamingReader fork = reader.forkValue()) {
                    fork.skipNext();
                    fork.endDocument();
                }
                reader.endDocument();
            }
        }
        try (StreamingReader reader = facade.createReader("1,")) {
            assertThrows(Exception.class, () -> {
                reader.peekToken();
                try (StreamingReader fork = reader.forkValue()) {
                    fork.skipNext();
                }
                reader.endDocument();
            });
        }
    }
}
