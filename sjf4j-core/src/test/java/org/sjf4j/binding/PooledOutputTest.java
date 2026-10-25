package org.sjf4j.binding;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PooledOutputTest {

    @Test
    void stringWriterSupportsBackendCloseAndDetachedResult() {
        PooledStringWriter writer = new PooledStringWriter();
        writer.write("hello ");
        writer.append("世界");
        writer.close(); // A backend may close the supplied Writer
        String json = writer.getAndRecycle();
        assertEquals("hello 世界", json);
        assertThrows(IllegalStateException.class, writer::toString);
        writer.recycle();
        try {
            PooledStringWriter another = new PooledStringWriter();
            another.write("another");
            assertEquals("another", another.getAndRecycle());
        } catch (Exception e) {
            fail(e);
        }
        assertEquals("hello 世界", json);
    }

    @Test
    void byteOutputSupportsBackendCloseAndDetachedResult() throws Exception {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        byte[] source = "hello 世界".getBytes(StandardCharsets.UTF_8);
        output.write(source);
        output.close();
        byte[] result = output.getAndRecycle();
        assertArrayEquals(source, result);
        assertThrows(IllegalStateException.class, output::toByteArray);
        try {
            PooledByteArrayOutputStream next = new PooledByteArrayOutputStream();
            next.write('x');
            assertArrayEquals(new byte[] {'x'}, next.getAndRecycle());
        } catch (Exception e) {
            fail(e);
        }
        assertArrayEquals(source, result);
    }

    @Test
    void nestedOutputDoesNotReuseCheckedOutArray() {
        PooledStringWriter outer = new PooledStringWriter();
        PooledStringWriter inner = new PooledStringWriter();
        try {
            outer.write("outer");
            inner.write("inner");
            assertEquals("inner", inner.getAndRecycle());
            assertEquals("outer", outer.getAndRecycle());
        } finally {
            inner.recycle();
            outer.recycle();
        }
    }

    @Test
    void oversizedOutputIsNotRetained() {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        byte[] large = new byte[OutputBufferRecycler.MAX_RETAINED_CAPACITY + 1];
        Arrays.fill(large, (byte) 9);
        output.write(large, 0, large.length);
        assertArrayEquals(large, output.getAndRecycle());
        byte[] next = OutputBufferRecycler.acquireBytes();
        try {
            assertTrue(next.length >= OutputBufferRecycler.INITIAL_CAPACITY);
            assertTrue(next.length <= OutputBufferRecycler.MAX_RETAINED_CAPACITY);
        } finally {
            OutputBufferRecycler.release(next);
        }
    }

    @Test
    void retainsSmallBufferAfterOversizedCharOutput() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write("x".repeat(100_000));
            assertEquals(100_000, writer.size());
        } finally {
            writer.recycle();
        }
        char[] reusable = OutputBufferRecycler.acquireChars();
        try {
            assertTrue(reusable.length <= OutputBufferRecycler.MAX_RETAINED_CAPACITY);
            assertTrue(reusable.length >= OutputBufferRecycler.INITIAL_CAPACITY);
        } finally {
            OutputBufferRecycler.release(reusable);
        }
    }

    @Test
    void expansionCopiesOnlyActiveContentForBothSinks() throws Exception {
        PooledStringWriter writer = new PooledStringWriter();
        PooledByteArrayOutputStream stream = new PooledByteArrayOutputStream();
        try {
            writer.write("prefix");
            char[] suffix = new char[128 * 1024];
            Arrays.fill(suffix, 'x');
            writer.write(suffix);
            assertEquals(6 + suffix.length, writer.size());
            assertEquals("prefix", writer.toString().substring(0, 6));
            assertEquals('x', writer.toString().charAt(writer.size() - 1));

            byte[] chunk = new byte[128 * 1024];
            Arrays.fill(chunk, (byte) 42);
            stream.write(new byte[] {1, 2, 3});
            stream.write(chunk);
            assertEquals(3 + chunk.length, stream.size());
            byte[] result = stream.getAndRecycle();
            assertArrayEquals(new byte[] {1, 2, 3}, Arrays.copyOf(result, 3));
            assertEquals(42, result[result.length - 1]);
        } finally {
            writer.recycle();
            stream.recycle();
        }
    }

    @Test
    void expansionUsesPowerOfTwoTiersUntil64KAndHeadroomBeyond() {
        assertEquals(1024, OutputBufferRecycler.growCapacity(512, 1024));
        assertEquals(2048, OutputBufferRecycler.growCapacity(1024, 1200));
        assertEquals(4096, OutputBufferRecycler.growCapacity(1024, 3000));
        assertEquals(8192, OutputBufferRecycler.growCapacity(4096, 5000));
        assertEquals(65536, OutputBufferRecycler.growCapacity(32768, 65536));
        assertEquals(98305, OutputBufferRecycler.growCapacity(65536, 65537));
        assertEquals(150000, OutputBufferRecycler.growCapacity(65536, 100000));
        assertEquals(Integer.MAX_VALUE - 8,
                OutputBufferRecycler.growCapacity(65536, Integer.MAX_VALUE - 8));
    }

    @Test
    void appendAndBounds() {
        PooledStringWriter out = new PooledStringWriter();
        try {
            out.append((CharSequence) null);
            out.append(new StringBuilder("abcdef"), 2, 4);
            out.write(new char[] {'x', 'y', 'z'}, 1, 2);
            assertEquals("nullcdyz", out.toString());
            assertThrows(IndexOutOfBoundsException.class,
                    () -> out.write(new char[2], 1, 2));
            out.reset();
            assertEquals("", out.toString());
        } finally {
            out.recycle();
        }
    }

    @Test
    void writerCoversSingleCharSubstringsAndAppendOverloads() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write('A');
            writer.append('B');
            writer.append((CharSequence) "CDE", 1, 3);
            writer.write("xyz", 1, 2);
            writer.append(new StringBuilder("12345"), 1, 4);
            writer.write(new char[] {'r', 's', 't'}, 0, 2);
            writer.append((CharSequence) null, 1, 3);
            writer.append((CharSequence) null);
            writer.write("", 0, 0);
            writer.write(new char[0], 0, 0);
            assertEquals("ABDEyz234rsulnull", writer.toString());
            assertEquals(17, writer.size());
            writer.flush();
            writer.reset();
            assertEquals(0, writer.size());
            writer.append("done");
            assertEquals("done", writer.getAndRecycle());
            assertThrows(IllegalStateException.class, () -> writer.write('x'));
            assertThrows(IllegalStateException.class, writer::reset);
            assertThrows(IllegalStateException.class, writer::size);
        } finally {
            writer.recycle();
        }
    }

    @Test
    void writerRejectsInvalidRangesWithoutChangingSize() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write("ok");
            assertThrows(IndexOutOfBoundsException.class, () -> writer.write("abc", 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> writer.write("abc", 3, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> writer.write(new char[3], 0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> writer.write(new char[3], 3, 1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> writer.append(new StringBuilder("abc"), -1, 2));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> writer.append(new StringBuilder("abc"), 2, 1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> writer.append(new StringBuilder("abc"), 0, 4));
            assertEquals("ok", writer.toString());
        } finally {
            writer.recycle();
        }
    }

    @Test
    void writerGrowsThroughCharAndStringPaths() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            char[] chars = new char[70_000];
            Arrays.fill(chars, 'x');
            writer.write(chars, 0, 65_535);
            writer.write('y');
            writer.write("z".repeat(4_464));
            assertEquals(70_000, writer.size());
            String detached = writer.getAndRecycle();
            assertEquals(70_000, detached.length());
            assertEquals('x', detached.charAt(0));
            assertEquals('y', detached.charAt(65_535));
            assertEquals('z', detached.charAt(69_999));
        } finally {
            writer.recycle();
        }
    }

    @Test
    void byteStreamCoversPrimitiveArrayResetAndBounds() {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
        try {
            out.write('A');
            out.write(new byte[] {1, 2, 3, 4}, 1, 2);
            out.write(new byte[0], 0, 0);
            assertEquals(3, out.size());
            assertArrayEquals(new byte[] {'A', 2, 3}, out.toByteArray());
            assertThrows(IndexOutOfBoundsException.class,
                    () -> out.write(new byte[3], -1, 1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> out.write(new byte[3], 1, 3));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> out.write(new byte[3], 1, -1));
            assertEquals(3, out.size());
            out.flush();
            out.reset();
            assertEquals(0, out.size());
            out.write(255);
            assertArrayEquals(new byte[] {(byte) 255}, out.getAndRecycle());
            assertThrows(IllegalStateException.class, out::size);
            assertThrows(IllegalStateException.class, () -> out.write(new byte[0], 0, 0));
        } finally {
            out.recycle();
        }
    }

    @Test
    void byteStreamGrowsWithIndividualWritesAndBulkWrites() {
        PooledByteArrayOutputStream out = new PooledByteArrayOutputStream();
        try {
            for (int i = 0; i < 2_050; i++) out.write(i);
            byte[] tail = new byte[70_000];
            Arrays.fill(tail, (byte) 42);
            out.write(tail, 0, tail.length);
            byte[] result = out.getAndRecycle();
            assertEquals(72_050, result.length);
            assertEquals((byte) 0, result[0]);
            assertEquals((byte) 42, result[result.length - 1]);
        } finally {
            out.recycle();
        }
    }

    @Test
    void recyclerPrefersLargerBoundedCharAndByteArrays() {
        // Checkout clears each slot, so this test does not depend on another test's cache.
        byte[] priorBytes = OutputBufferRecycler.acquireBytes();
        char[] priorChars = OutputBufferRecycler.acquireChars();
        try {
            byte[] smallBytes = new byte[2048];
            byte[] largeBytes = new byte[8192];
            OutputBufferRecycler.release(smallBytes);
            OutputBufferRecycler.release(largeBytes);
            OutputBufferRecycler.release(new byte[4096]);
            assertSame(largeBytes, OutputBufferRecycler.acquireBytes());

            char[] smallChars = new char[2048];
            char[] largeChars = new char[8192];
            OutputBufferRecycler.release(smallChars);
            OutputBufferRecycler.release(largeChars);
            OutputBufferRecycler.release(new char[4096]);
            assertSame(largeChars, OutputBufferRecycler.acquireChars());

            OutputBufferRecycler.release(largeBytes);
            OutputBufferRecycler.release(largeChars);
        } finally {
            OutputBufferRecycler.release(priorBytes);
            OutputBufferRecycler.release(priorChars);
        }
    }

    @Test
    void recyclerDoesNotCacheOversizedBuffers() {
        byte[] oldBytes = OutputBufferRecycler.acquireBytes();
        char[] oldChars = OutputBufferRecycler.acquireChars();
        try {
            OutputBufferRecycler.release(new byte[OutputBufferRecycler.MAX_RETAINED_CAPACITY + 1]);
            OutputBufferRecycler.release(new char[OutputBufferRecycler.MAX_RETAINED_CAPACITY + 1]);
            byte[] freshBytes = OutputBufferRecycler.acquireBytes();
            char[] freshChars = OutputBufferRecycler.acquireChars();
            try {
                assertEquals(OutputBufferRecycler.INITIAL_CAPACITY, freshBytes.length);
                assertEquals(OutputBufferRecycler.INITIAL_CAPACITY, freshChars.length);
            } finally {
                OutputBufferRecycler.release(freshBytes);
                OutputBufferRecycler.release(freshChars);
            }
        } finally {
            OutputBufferRecycler.release(oldBytes);
            OutputBufferRecycler.release(oldChars);
        }
    }

    @Test
    void recyclerSupportsDirectOwnershipAndNestedCheckout() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        char[] previous = recycler.takeChars();
        try {
            char[] candidate = new char[4096];
            recycler.recycleChars(candidate);
            assertSame(candidate, recycler.takeChars());
            char[] nested = recycler.takeChars();
            assertNotSame(candidate, nested);
            recycler.recycleChars(nested);
            recycler.recycleChars(candidate);
            assertSame(candidate, recycler.takeChars());
            recycler.recycleChars(new char[OutputBufferRecycler.MAX_RETAINED_CAPACITY + 1]);
        } finally {
            recycler.recycleChars(previous);
        }
    }

    @Test
    void expansionRejectsInvalidAndOversizedRequirements() {
        assertThrows(OutOfMemoryError.class,
                () -> OutputBufferRecycler.growCapacity(1024, -1));
        assertThrows(OutOfMemoryError.class,
                () -> OutputBufferRecycler.growCapacity(1024, Integer.MAX_VALUE));
        assertEquals(1024, OutputBufferRecycler.growCapacity(1024, 1));
        assertEquals(2048, OutputBufferRecycler.growCapacity(1024, 1025));
        assertEquals(65536, OutputBufferRecycler.growCapacity(32768, 65536));
        assertEquals(98305, OutputBufferRecycler.growCapacity(65536, 65537));
    }

}
