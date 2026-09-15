package org.sjf4j.binding;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastStringReaderTest {

    @Test
    void readsSingleCharactersAndBulk() throws IOException {
        FastStringReader reader = new FastStringReader("abcd");

        assertEquals('a', reader.read());
        char[] chars = new char[4];
        assertEquals(3, reader.read(chars, 1, 3));
        assertArrayEquals(new char[] {'\0', 'b', 'c', 'd'}, chars);
    }

    @Test
    void returnsEofAndZeroLengthRead() throws IOException {
        FastStringReader reader = new FastStringReader("");

        assertEquals(0, reader.read(new char[0], 0, 0));
        assertEquals(-1, reader.read());
        assertEquals(-1, reader.read(new char[1], 0, 1));
    }

    @Test
    void validatesConstructorAndReadArguments() {
        assertThrows(NullPointerException.class, () -> new FastStringReader(null));

        FastStringReader reader = new FastStringReader("a");
        assertThrows(NullPointerException.class, () -> reader.read(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(new char[1], -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(new char[1], 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(new char[1], 0, -1));
    }

    @Test
    void skipsForwardAndBackward() throws IOException {
        FastStringReader reader = new FastStringReader("abcd");

        assertEquals(2, reader.skip(2));
        assertEquals(-1, reader.skip(-1));
        assertEquals('b', reader.read());
        assertEquals(-2, reader.skip(-10));
        assertEquals('a', reader.read());
        assertEquals(3, reader.skip(10));
        assertEquals(0, reader.skip(-1));

        reader = new FastStringReader("a");
        assertEquals(1, reader.skip(Long.MAX_VALUE));
        reader = new FastStringReader("a");
        assertEquals(0, reader.skip(Long.MIN_VALUE));
    }

    @Test
    void supportsMarkResetAndReady() throws IOException {
        FastStringReader reader = new FastStringReader("ab");

        assertTrue(reader.markSupported());
        assertTrue(reader.ready());
        assertEquals('a', reader.read());
        reader.mark(0);
        assertEquals('b', reader.read());
        reader.reset();
        assertEquals('b', reader.read());
        FastStringReader markedReader = reader;
        assertThrows(IllegalArgumentException.class, () -> markedReader.mark(-1));

        reader = new FastStringReader("a");
        reader.read();
        reader.reset();
        assertEquals('a', reader.read());
    }

    @Test
    void rejectsExposedOperationsAfterClose() throws IOException {
        FastStringReader reader = new FastStringReader("a");
        reader.close();

        assertThrows(IOException.class, reader::read);
        assertThrows(IOException.class, () -> reader.read(new char[0], 0, 0));
        assertThrows(IOException.class, () -> reader.read(new char[1], -1, 1));
        assertThrows(IOException.class, reader::ready);
        assertThrows(IOException.class, () -> reader.skip(0));
        assertThrows(IOException.class, () -> reader.mark(0));
        assertThrows(IllegalArgumentException.class, () -> reader.mark(-1));
        assertThrows(IOException.class, reader::reset);
        reader.close();
    }
}
