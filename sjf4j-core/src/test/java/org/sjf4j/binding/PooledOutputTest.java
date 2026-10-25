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
            assertEquals(OutputBufferRecycler.INITIAL_CAPACITY, next.length);
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
            assertEquals(OutputBufferRecycler.INITIAL_CAPACITY, reusable.length);
        } finally {
            OutputBufferRecycler.release(reusable);
        }
    }

    @Test
    void expansionUsesRequiredHeadroom() {
        assertEquals(2048, OutputBufferRecycler.growCapacity(1024, 1200));
        assertEquals(4500, OutputBufferRecycler.growCapacity(1024, 3000));
        assertEquals(8192, OutputBufferRecycler.growCapacity(4096, 5000));
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
}
