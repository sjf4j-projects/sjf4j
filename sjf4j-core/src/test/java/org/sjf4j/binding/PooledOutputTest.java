package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PooledOutputTest {

    @BeforeEach
    void isolateThreadLocalRecyclerSlots() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        recycler.takeLargeBytes(1);
        recycler.takeLargeChars(1);
        recycler.takeBytes();
        recycler.takeChars();
    }

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
    void oversizedByteOutputRecyclesIntoSoftCache() {
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        byte[] large = new byte[OutputBufferRecycler.MAX_STRONG_RETAINED_CAPACITY + 1];
        Arrays.fill(large, (byte) 9);
        output.write(large, 0, large.length);
        assertArrayEquals(large, output.getAndRecycle());
        byte[] next = OutputBufferRecycler.acquireBytes();
        try {
            assertTrue(next.length >= large.length);
        } finally {
            OutputBufferRecycler.release(next);
        }
    }

    @Test
    void recyclesCharOutputWithoutLosingContent() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write("x".repeat(100_000));
            assertEquals(100_000, writer.size());
        } finally {
            writer.recycle();
        }
        char[] reusable = OutputBufferRecycler.acquireChars();
        try {
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
    void recyclerReuses128KCharAndByteOutputsThroughSoftCache() {
        // Checkout existing caches first so the initial capacity is deterministic.
        char[] previousChars = OutputBufferRecycler.acquireChars();
        byte[] previousBytes = OutputBufferRecycler.acquireBytes();
        try {
        // Two 64K writes exercise contiguous array growth without direct String pass-through.
        String contents = "x".repeat(128 * 1024);
        PooledStringWriter writer = new PooledStringWriter();
        writer.write(contents, 0, 64 * 1024);
        writer.write(contents, 64 * 1024, 64 * 1024);
        assertEquals(contents, writer.getAndRecycle());
        char[] cachedChars = OutputBufferRecycler.acquireChars();
        try {
            assertEquals(128 * 1024, cachedChars.length);
        } finally {
            OutputBufferRecycler.release(cachedChars);
        }

        byte[] payload = new byte[128 * 1024];
        Arrays.fill(payload, (byte) 7);
        PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
        output.write(payload, 0, payload.length);
        assertArrayEquals(payload, output.getAndRecycle());
        byte[] cachedBytes = OutputBufferRecycler.acquireBytes();
        try {
            assertEquals(128 * 1024, cachedBytes.length);
        } finally {
            OutputBufferRecycler.release(cachedBytes);
        }
        } finally {
            OutputBufferRecycler.release(previousChars);
            OutputBufferRecycler.release(previousBytes);
        }
    }

    @Test
    void recyclerPrefersSoftArraysBeyond64KCap() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        char[] priorLargeChars = recycler.takeLargeChars(1);
        byte[] priorLargeBytes = recycler.takeLargeBytes(1);
        char[] priorChars = recycler.takeChars();
        byte[] priorBytes = recycler.takeBytes();
        try {
            char[] softChars = new char[OutputBufferRecycler.MAX_STRONG_RETAINED_CAPACITY + 1];
            byte[] softBytes = new byte[OutputBufferRecycler.MAX_STRONG_RETAINED_CAPACITY + 1];
            recycler.recycleChars(softChars);
            recycler.recycleBytes(softBytes);
            assertSame(softChars, recycler.takeChars());
            assertSame(softBytes, recycler.takeBytes());
        } finally {
            if (priorLargeChars != null) recycler.recycleChars(priorLargeChars);
            if (priorLargeBytes != null) recycler.recycleBytes(priorLargeBytes);
            recycler.recycleChars(priorChars);
            recycler.recycleBytes(priorBytes);
        }
    }

    @Test
    void expansionUsesPowerOfTwoTiersUntil64KAndAdaptiveGrowthBeyond() {
        assertEquals(1024, OutputBufferRecycler.growCapacity(512, 0, 1024));
        assertEquals(2048, OutputBufferRecycler.growCapacity(1024, 0, 1200));
        assertEquals(4096, OutputBufferRecycler.growCapacity(1024, 0, 3000));
        assertEquals(8192, OutputBufferRecycler.growCapacity(4096, 0, 5000));
        assertEquals(65536, OutputBufferRecycler.growCapacity(32768, 0, 65536));
        assertEquals(65537, OutputBufferRecycler.growCapacity(65536, 0, 65537));
        assertEquals(100000, OutputBufferRecycler.growCapacity(65536, 0, 100000));
        assertEquals(98304, OutputBufferRecycler.growCapacity(65536, 65536, 65537));
        assertEquals(262144, OutputBufferRecycler.growCapacity(131072, 0, 262144));
        assertEquals(300000, OutputBufferRecycler.growCapacity(131072, 0, 300000));
        assertEquals(300000, OutputBufferRecycler.growCapacity(262144, 0, 300000));
        assertEquals(900000, OutputBufferRecycler.growCapacity(524288, 0, 900000));
        assertEquals(Integer.MAX_VALUE - 8,
                OutputBufferRecycler.growCapacity(65536, Integer.MAX_VALUE - 8));
    }

    @Test
    void writerGrowsPast256KAndCachesOversizedArray() {
        char[] oldCache = OutputBufferRecycler.acquireChars();
        try {
            PooledStringWriter writer = new PooledStringWriter();
            try {
                writer.write("x".repeat(256 * 1024));
                assertEquals(256 * 1024, writer.size());
                writer.append('A');
                writer.append(new StringBuilder("世界"));
                writer.append((CharSequence) null, 1, 3);
                assertEquals(256 * 1024 + 5, writer.size());
                String detached = writer.getAndRecycle();
                assertEquals('A', detached.charAt(256 * 1024));
                assertTrue(detached.endsWith("A世界ul"));
                assertThrows(IllegalStateException.class, writer::size);
            } finally {
                writer.recycle();
            }
            char[] cached = OutputBufferRecycler.acquireChars();
            assertTrue(cached.length >= 256 * 1024 + 5);
            OutputBufferRecycler.release(cached);
        } finally {
            OutputBufferRecycler.release(oldCache);
        }
    }

    @Test
    void writerSupportsDirectOversizeResetAndReusesArray() {
        char[] oldCache = OutputBufferRecycler.acquireChars();
        try {
            PooledStringWriter writer = new PooledStringWriter();
            try {
                char[] large = new char[300 * 1024];
                Arrays.fill(large, 'x');
                writer.write(large, 0, large.length);
                writer.write("中", 0, 1);
                assertEquals(large.length + 1, writer.size());
                assertEquals('中', writer.toString().charAt(large.length));
                writer.reset();
                assertEquals(0, writer.size());
                writer.write("fresh");
                assertEquals("fresh", writer.getAndRecycle());
            } finally {
                writer.recycle();
            }
            char[] cached = OutputBufferRecycler.acquireChars();
            assertTrue(cached.length >= 300 * 1024 + 1);
            OutputBufferRecycler.release(cached);
        } finally {
            OutputBufferRecycler.release(oldCache);
        }
    }

    @Test
    void byteOutputBeyond256KRetains256KAndGrowsWithoutClamping() {
        byte[] oldCache = OutputBufferRecycler.acquireBytes();
        try {
            byte[] payload = new byte[300 * 1024];
            Arrays.fill(payload, (byte) 31);
            PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
            try {
                output.write(payload, 0, payload.length);
                assertEquals(payload.length, output.size());
                output.write(42);
                assertEquals(payload.length + 1, output.size());
                byte[] result = output.getAndRecycle();
                assertEquals(31, result[0]);
                assertEquals(31, result[payload.length - 1]);
                assertEquals(42, result[payload.length]);
            } finally {
                output.recycle();
            }
            byte[] cached = OutputBufferRecycler.acquireBytes();
            assertTrue(cached.length >= payload.length + 1);
            OutputBufferRecycler.release(cached);
        } finally {
            OutputBufferRecycler.release(oldCache);
        }
    }

    @Test
    void largeModeRejectsBadRangesAndCannotWriteAfterRecycle() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write("x".repeat(300 * 1024));
            int size = writer.size();
            assertThrows(IndexOutOfBoundsException.class,
                    () -> writer.write(new char[2], 1, 2));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> writer.write("abc", 3, 1));
            assertEquals(size, writer.size());
            writer.getAndRecycle();
            assertThrows(IllegalStateException.class, () -> writer.write('a'));
            assertThrows(IllegalStateException.class, () -> writer.append(new StringBuilder("x")));
        } finally {
            writer.recycle();
        }
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
    void recyclerFallsBackToStrongBuffersAfterSoftCheckout() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        byte[] oldSoftBytes = recycler.takeLargeBytes(1);
        char[] oldSoftChars = recycler.takeLargeChars(1);
        byte[] oldBytes = recycler.takeBytes();
        char[] oldChars = recycler.takeChars();
        try {
            byte[] smallBytes = new byte[2048];
            char[] smallChars = new char[2048];
            byte[] largeBytes = new byte[1024 * 1024];
            char[] largeChars = new char[1024 * 1024];
            recycler.recycleBytes(smallBytes);
            recycler.recycleChars(smallChars);
            recycler.recycleBytes(largeBytes);
            recycler.recycleChars(largeChars);
            assertSame(largeBytes, recycler.takeBytes());
            assertSame(largeChars, recycler.takeChars());
            assertSame(smallBytes, recycler.takeBytes());
            assertSame(smallChars, recycler.takeChars());
        } finally {
            if (oldSoftBytes != null) recycler.recycleBytes(oldSoftBytes);
            if (oldSoftChars != null) recycler.recycleChars(oldSoftChars);
            recycler.recycleBytes(oldBytes);
            recycler.recycleChars(oldChars);
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
            recycler.recycleChars(new char[OutputBufferRecycler.MAX_STRONG_RETAINED_CAPACITY + 1]);
        } finally {
            recycler.recycleChars(previous);
        }
    }

    @Test
    void adaptiveGrowthUsesExistingCapacityForSmallAppends() {
        assertEquals(300000, OutputBufferRecycler.growCapacity(262144, 0, 300000));
        assertEquals(393216, OutputBufferRecycler.growCapacity(262144, 200000, 300000));
        assertEquals(450000, OutputBufferRecycler.growCapacity(300000, 299999, 300001));
        assertEquals(600000, OutputBufferRecycler.growCapacity(400000, 400000, 400001));
        assertEquals(900000, OutputBufferRecycler.growCapacity(800000, 400000, 900000));
        assertEquals(800000, OutputBufferRecycler.growCapacity(800000, 400000, 800000));
    }

    @Test
    void oversizedArraysRetainOnlyPreviouslyUsedBuffers() {
        char[] previousChars = OutputBufferRecycler.acquireChars();
        byte[] previousBytes = OutputBufferRecycler.acquireBytes();
        try {
            String chars = "x".repeat(300 * 1024);
            PooledStringWriter writer = new PooledStringWriter();
            writer.write(chars);
            assertEquals(chars, writer.getAndRecycle());

            byte[] bytes = new byte[300 * 1024];
            Arrays.fill(bytes, (byte) 33);
            PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
            output.write(bytes, 0, bytes.length);
            assertArrayEquals(bytes, output.getAndRecycle());

            char[] cachedChars = OutputBufferRecycler.acquireChars();
            byte[] cachedBytes = OutputBufferRecycler.acquireBytes();
            try {
                assertEquals(OutputBufferRecycler.INITIAL_CAPACITY, cachedChars.length);
                assertTrue(cachedBytes.length >= bytes.length);
            } finally {
                OutputBufferRecycler.release(cachedChars);
                OutputBufferRecycler.release(cachedBytes);
            }
        } finally {
            OutputBufferRecycler.release(previousChars);
            OutputBufferRecycler.release(previousBytes);
        }
    }

    @Test
    void byteGrowthUsesDoublingForSmallAppendsAndExactForBulk() {
        assertEquals(262144, OutputBufferRecycler.growBytesCapacity(131072, 120000, 180000));
        assertEquals(300000, OutputBufferRecycler.growBytesCapacity(262144, 0, 300000));
        assertEquals(524288, OutputBufferRecycler.growBytesCapacity(262144, 262144, 262145));
        assertEquals(1048576, OutputBufferRecycler.growBytesCapacity(524288, 524288, 524289));
        assertEquals(900000, OutputBufferRecycler.growBytesCapacity(524288, 100000, 900000));
        assertEquals(Integer.MAX_VALUE - 8,
                OutputBufferRecycler.growBytesCapacity(1073741824, 1073741824, 1073741825));
    }

    @Test
    void expansionRejectsInvalidAndOversizedRequirements() {
        assertThrows(OutOfMemoryError.class,
                () -> OutputBufferRecycler.growCapacity(1024, -1));
        assertThrows(OutOfMemoryError.class,
                () -> OutputBufferRecycler.growCapacity(1024, Integer.MAX_VALUE));
        assertEquals(1024, OutputBufferRecycler.growCapacity(1024, 0, 1));
        assertEquals(2048, OutputBufferRecycler.growCapacity(1024, 0, 1025));
        assertEquals(65536, OutputBufferRecycler.growCapacity(32768, 0, 65536));
        assertEquals(65537, OutputBufferRecycler.growCapacity(65536, 0, 65537));
        assertEquals(98304, OutputBufferRecycler.growCapacity(65536, 65536, 65537));
    }


    @Test
    void stringWriterGrowsPast64KAndPreservesMixedContent() {
        String prefix = "a".repeat(256 * 1024 - 2);
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write(prefix);
            writer.append("中");
            writer.write('x');
            assertEquals(256 * 1024, writer.size());
            assertEquals(prefix + "中x", writer.toString());
            writer.append("界");
            writer.append(new StringBuilder("-suffix"), 0, 7);
            assertEquals(prefix + "中x界-suffix", writer.getAndRecycle());
            assertThrows(IllegalStateException.class, writer::size);
        } finally {
            writer.recycle();
        }
    }

    @Test
    void stringWriterLargeDirectWritesAndResetReleaseLargeStorage() {
        String source = "中文abc".repeat(80_000);
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write(source, 1, 350_000);
            assertEquals(source.substring(1, 350_001), writer.toString());
            assertThrows(IndexOutOfBoundsException.class, () -> writer.write(source, -1, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> writer.write(new char[2], 1, 2));
            assertEquals(350_000, writer.size());
            writer.reset();
            assertEquals(0, writer.size());
            writer.write("short");
            assertEquals("short", writer.getAndRecycle());
        } finally {
            writer.recycle();
        }
    }

    @Test
    void stringWriterLargeCharArrayAndCharSequenceWrites() {
        char[] chars = new char[300 * 1024];
        Arrays.fill(chars, '字');
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write(chars, 0, chars.length);
            writer.append((CharSequence) new StringBuilder("suffix"), 0, 6);
            writer.write('!');
            assertEquals("字".repeat(chars.length) + "suffix!", writer.getAndRecycle());
        } finally {
            writer.recycle();
        }
    }

    @Test
    void firstLargeStringWriteHandlesLatin1UnicodeAndSubranges() {
        PooledStringWriter writer = new PooledStringWriter();
        try {
            String latin = "a".repeat(300 * 1024);
            writer.write(latin);
            assertEquals(latin, writer.toString());
            writer.reset();
            String unicode = "世界".repeat(160 * 1024);
            writer.write(unicode);
            writer.append("!");
            assertEquals(unicode + "!", writer.getAndRecycle());
        } finally {
            writer.recycle();
        }

        PooledStringWriter ranged = new PooledStringWriter();
        try {
            String source = "[" + "中文".repeat(160 * 1024) + "]";
            ranged.write(source, 1, source.length() - 2);
            assertEquals(source.substring(1, source.length() - 1), ranged.getAndRecycle());
        } finally {
            ranged.recycle();
        }
    }

    @Test
    void softLargeCachesAreSeparateFromBoundedCachesAndPreferLargestArray() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        byte[] priorSmallBytes = recycler.takeBytes();
        char[] priorSmallChars = recycler.takeChars();
        byte[] priorLargeBytes = recycler.takeLargeBytes(1);
        char[] priorLargeChars = recycler.takeLargeChars(1);
        try {
            byte[] largeBytes = new byte[512 * 1024];
            char[] largeChars = new char[384 * 1024];
            recycler.recycleBytes(largeBytes);
            recycler.recycleBytes(new byte[300 * 1024]);
            recycler.recycleChars(largeChars);
            recycler.recycleChars(new char[300 * 1024]);

            assertNull(recycler.takeLargeBytes(600 * 1024));
            assertNull(recycler.takeLargeChars(600 * 1024));
            assertSame(largeBytes, recycler.takeLargeBytes(300 * 1024));
            assertSame(largeChars, recycler.takeLargeChars(300 * 1024));
            assertNull(recycler.takeLargeBytes(1));
            assertNull(recycler.takeLargeChars(1));

            byte[] boundedBytes = new byte[64 * 1024];
            char[] boundedChars = new char[64 * 1024];
            recycler.recycleBytes(boundedBytes);
            recycler.recycleChars(boundedChars);
            assertSame(boundedBytes, recycler.takeBytes());
            assertSame(boundedChars, recycler.takeChars());
        } finally {
            if (priorLargeBytes != null) recycler.recycleBytes(priorLargeBytes);
            if (priorLargeChars != null) recycler.recycleChars(priorLargeChars);
            recycler.recycleBytes(priorSmallBytes);
            recycler.recycleChars(priorSmallChars);
        }
    }

    @Test
    void byteOutputReusesSoftCachedLargeArrayWithoutEagerAllocation() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        byte[] priorSmall = recycler.takeBytes();
        byte[] priorLarge = recycler.takeLargeBytes(1);
        try {
            byte[] cached = new byte[512 * 1024];
            recycler.recycleBytes(cached);
            byte[] payload = new byte[300 * 1024];
            Arrays.fill(payload, (byte) 23);
            PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
            assertArrayEquals(payload, writeAndRecycle(output, payload));

            // The exact checked-out array was returned to the soft cache.
            assertSame(cached, recycler.takeLargeBytes(payload.length));
            assertNull(recycler.takeLargeBytes(payload.length));
        } finally {
            if (priorLarge != null) recycler.recycleBytes(priorLarge);
            recycler.recycleBytes(priorSmall);
        }
    }

    private static byte[] writeAndRecycle(PooledByteArrayOutputStream output, byte[] payload) {
        try {
            output.write(payload, 0, payload.length);
            return output.getAndRecycle();
        } finally {
            output.recycle();
        }
    }

    @Test
    void oneKilobyteOutputReusesOneMegabyteSoftBufferWithoutFullCopy() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        byte[] oldSoftBytes = recycler.takeLargeBytes(1);
        char[] oldSoftChars = recycler.takeLargeChars(1);
        byte[] oldSmallBytes = recycler.takeBytes();
        char[] oldSmallChars = recycler.takeChars();
        try {
            byte[] largeBytes = new byte[1024 * 1024];
            char[] largeChars = new char[1024 * 1024];
            recycler.recycleBytes(largeBytes);
            recycler.recycleChars(largeChars);
            byte[] input = new byte[1024];
            Arrays.fill(input, (byte) 77);

            PooledByteArrayOutputStream output = new PooledByteArrayOutputStream();
            output.write(input, 0, input.length);
            assertArrayEquals(input, output.getAndRecycle());
            assertSame(largeBytes, recycler.takeLargeBytes(1));

            PooledStringWriter writer = new PooledStringWriter();
            String content = "A".repeat(1024);
            writer.write(content);
            assertEquals(content, writer.getAndRecycle());
            assertSame(largeChars, recycler.takeLargeChars(1));
        } finally {
            if (oldSoftBytes != null) recycler.recycleBytes(oldSoftBytes);
            if (oldSoftChars != null) recycler.recycleChars(oldSoftChars);
            recycler.recycleBytes(oldSmallBytes);
            recycler.recycleChars(oldSmallChars);
        }
    }

    @Test
    void directLargeStringReturnsOriginalAndCopiesOnlyOnAppend() {
        for (String input : new String[]{"a".repeat(300 * 1024), "中文".repeat(160 * 1024)}) {
            PooledStringWriter writer = new PooledStringWriter();
            try {
                writer.write(input);
                assertSame(input, writer.toString());
                assertEquals(input.length(), writer.size());
                writer.write("", 0, 0);
                assertSame(input, writer.toString());
                writer.append(new StringBuilder());
                assertSame(input, writer.toString());
                writer.write('!');
                assertEquals(input + "!", writer.getAndRecycle());
            } finally {
                writer.recycle();
            }
        }
    }

    @Test
    void directLargeStringSupportsAllAppendPathsAndReset() {
        String input = "界".repeat(300 * 1024);
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write(input);
            writer.write(new char[]{'A', 'B'}, 0, 2);
            writer.append(new StringBuilder("CD"), 0, 2);
            writer.write("EF", 0, 2);
            assertEquals(input + "ABCDEF", writer.toString());
            writer.reset();
            assertEquals(0, writer.size());
            writer.write("small");
            assertEquals("small", writer.getAndRecycle());
        } finally {
            writer.recycle();
        }
    }

    @Test
    void directLargeStringNestedWriterAndRecycleMaintainOwnership() {
        String input = "x".repeat(300 * 1024);
        PooledStringWriter outer = new PooledStringWriter();
        PooledStringWriter inner = new PooledStringWriter();
        try {
            outer.write(input);
            inner.write("inner");
            assertSame(input, outer.getAndRecycle());
            assertEquals("inner", inner.getAndRecycle());
            assertThrows(IllegalStateException.class, outer::toString);
        } finally {
            outer.recycle();
            inner.recycle();
        }
    }

    @Test
    void buffersBeyond64KAreSoftWhile64KRemainsStrong() {
        OutputBufferRecycler recycler = OutputBufferRecycler.current();
        byte[] strongBytes = new byte[64 * 1024];
        char[] strongChars = new char[64 * 1024];
        byte[] softBytes = new byte[128 * 1024];
        char[] softChars = new char[128 * 1024];

        recycler.recycleBytes(strongBytes);
        recycler.recycleChars(strongChars);
        recycler.recycleBytes(softBytes);
        recycler.recycleChars(softChars);

        // Large arrays must never occupy the strongly retained slots.
        assertSame(softBytes, recycler.takeLargeBytes(1));
        assertSame(softChars, recycler.takeLargeChars(1));
        assertSame(strongBytes, recycler.takeBytes());
        assertSame(strongChars, recycler.takeChars());
        recycler.recycleBytes(softBytes);
        recycler.recycleChars(softChars);
        recycler.recycleBytes(strongBytes);
        recycler.recycleChars(strongChars);

        // Even a power-of-two tier beyond 64K uses the soft slots.
        byte[] justOver = new byte[64 * 1024 + 1];
        char[] justOverChars = new char[64 * 1024 + 1];
        recycler.recycleBytes(justOver);
        recycler.recycleChars(justOverChars);
        assertSame(softBytes, recycler.takeLargeBytes(1));
        assertSame(softChars, recycler.takeLargeChars(1));
    }

    @Test
    void growthAndDirectStringThresholdAre64K() {
        assertEquals(65537, OutputBufferRecycler.growCapacity(65536, 0, 65537));
        assertEquals(98304, OutputBufferRecycler.growCapacity(65536, 65536, 65537));
        assertEquals(131073, OutputBufferRecycler.growCapacity(131072, 0, 131073));

        PooledStringWriter medium = new PooledStringWriter();
        try {
            String source = "abc".repeat(43_691); // >64K, <256K
            medium.write(source);
            assertSame(source, medium.toString());
            assertEquals(source, medium.getAndRecycle());
        } finally {
            medium.recycle();
        }

        PooledStringWriter large = new PooledStringWriter();
        try {
            String source = "汉".repeat(262145);
            large.write(source);
            assertSame(source, large.getAndRecycle());
        } finally {
            large.recycle();
        }
    }

    @Test
    void oneShotStringUsesZeroCopyImmediatelyAbove64K() {
        String small = "x".repeat(64 * 1024);
        String big = "界".repeat(64 * 1024 + 1);
        PooledStringWriter writer = new PooledStringWriter();
        try {
            writer.write(small);
            assertNotSame(small, writer.toString());
            assertEquals(small, writer.toString());
            writer.reset();
            writer.write(big);
            assertSame(big, writer.toString());
            writer.append('!');
            assertEquals(big + "!", writer.getAndRecycle());
        } finally {
            writer.recycle();
        }
    }

    @Test
    void byteGrowthSwitchesFromPowerOfTwoToAdaptiveAt64K() {
        assertEquals(65536, OutputBufferRecycler.growBytesCapacity(32768, 0, 65536));
        assertEquals(65537, OutputBufferRecycler.growBytesCapacity(65536, 0, 65537));
        assertEquals(131072, OutputBufferRecycler.growBytesCapacity(65536, 65536, 65537));
        assertEquals(100000, OutputBufferRecycler.growBytesCapacity(65536, 0, 100000));
    }
}
