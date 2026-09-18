package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Default array bindings from Jackson's ArrayDeserializationTest. */
public abstract class ArrayDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);

    /** Source: ArrayDeserializationTest#testUntypedArray. */
    @Test void testUntypedArray() {
        Object[] value = (Object[]) binding(StreamingContext.EMPTY).readNode("[1,null,\"x\",true,2.0]", Object[].class);
        assertEquals(5, value.length); assertEquals(1, value[0]); assertNull(value[1]); assertEquals("x", value[2]); assertEquals(true, value[3]); assertEquals(2.0, value[4]);
    }

    /** Source: ArrayDeserializationTest#testIntegerArray and #testStringArray. */
    @Test void testObjectArrays() {
        assertArrayEquals(new Integer[] { 1, -2 }, (Integer[]) binding(StreamingContext.EMPTY).readNode("[1,-2]", Integer[].class));
        assertArrayEquals(new String[] { "a", null, "b" }, (String[]) binding(StreamingContext.EMPTY).readNode("[\"a\",null,\"b\"]", String[].class));
    }

    /** Source: ArrayDeserializationTest#testBooleanArray, #testShortArray, #testIntArray, #testLongArray, #testFloatArray, and #testDoubleArray. */
    @Test void testPrimitiveArrays() {
        assertArrayEquals(new boolean[] { true, false }, (boolean[]) binding(StreamingContext.EMPTY).readNode("[true,false]", boolean[].class));
        assertArrayEquals(new short[] { 1, -2 }, (short[]) binding(StreamingContext.EMPTY).readNode("[1,-2]", short[].class));
        assertArrayEquals(new int[] { 1, -2 }, (int[]) binding(StreamingContext.EMPTY).readNode("[1,-2]", int[].class));
        assertArrayEquals(new long[] { 1L, -2L }, (long[]) binding(StreamingContext.EMPTY).readNode("[1,-2]", long[].class));
        assertArrayEquals(new float[] { 1.5f, -2f }, (float[]) binding(StreamingContext.EMPTY).readNode("[1.5,-2]", float[].class));
        assertArrayEquals(new double[] { 1.5d, -2d }, (double[]) binding(StreamingContext.EMPTY).readNode("[1.5,-2]", double[].class));
    }

    /** Source: ArrayDeserializationTest#testCharArray. */
    @Test void testCharArray() { assertArrayEquals("abc".toCharArray(), (char[]) binding(StreamingContext.EMPTY).readNode("[\"a\",\"b\",\"c\"]", char[].class)); }

    /** Source: ArrayDeserializationTest#testByteArrayAsNumbers. */
    @Test void testByteArrayAsNumbers() { assertArrayEquals(new byte[] { -1, 0, 127 }, (byte[]) binding(StreamingContext.EMPTY).readNode("[-1,0,127]", byte[].class)); }
}
