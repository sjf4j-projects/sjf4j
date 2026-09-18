package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.StreamingContext;
import org.sjf4j.binding.JsonBinder;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Portable scalar cases from Jackson's JDKScalarsDeserTest. */
public abstract class JDKScalarsDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(StreamingContext context);
    /** Source: JDKScalarsDeserTest#testBooleanWrapper. */
    @Test void testBooleanWrapper() {
        assertEquals(Boolean.TRUE, binding(StreamingContext.EMPTY).readNode("true", Boolean.class));
        assertEquals(Boolean.FALSE, binding(StreamingContext.EMPTY).readNode("false", Boolean.class));
    }
    /** Source: JDKScalarsDeserTest#testCharacterWrapper (one-character-string case). */
    @Test void testCharacterWrapper() { assertEquals('x', binding(StreamingContext.EMPTY).readNode("\"x\"", Character.class)); }
    /** Source: JDKScalarsDeserTest#testIntWrapper and #testLongWrapper (native numeric input). */
    @Test void testIntegerAndLongWrapper() {
        assertEquals(-42, binding(StreamingContext.EMPTY).readNode("-42", Integer.class));
        assertEquals(12345678901L, binding(StreamingContext.EMPTY).readNode("12345678901", Long.class));
    }
    /** Source: JDKScalarsDeserTest#testNullForPrimitivesDefault. Kept failing until primitive null assignment is fixed. */
    @Test void testNullForPrimitivesDefault() {
        Primitives value = (Primitives) binding(StreamingContext.EMPTY).readNode("{\"booleanValue\":null,\"byteValue\":null,\"charValue\":null,\"shortValue\":null,\"intValue\":null,\"longValue\":null,\"floatValue\":null,\"doubleValue\":null}", Primitives.class);
        assertEquals(false, value.booleanValue); assertEquals((byte) 0, value.byteValue); assertEquals('\0', value.charValue);
        assertEquals((short) 0, value.shortValue); assertEquals(0, value.intValue); assertEquals(0L, value.longValue);
        assertEquals(0f, value.floatValue); assertEquals(0d, value.doubleValue);
    }
    /** Source: JDKScalarsDeserTest#testNullForPrimitiveArrays. Kept failing until primitive null array elements are fixed. */
    @Test void testNullForPrimitiveArrays() {
        assertArrayEquals(new int[] {0}, (int[]) binding(StreamingContext.EMPTY).readNode("[null]", int[].class));
        assertArrayEquals(new boolean[] {false}, (boolean[]) binding(StreamingContext.EMPTY).readNode("[null]", boolean[].class));
    }
    /** Source: JDKScalarsDeserTest#testNullForPrimitivesDefault (reference null contrast). */
    @Test void testNullForWrapperProperty() { assertNull(((Wrappers) binding(StreamingContext.EMPTY).readNode("{\"value\":null}", Wrappers.class)).value); }
    /** Retained SJF4J scalar-root coverage; JDKScalarsDeserTest has no successful BigInteger/BigDecimal root counterpart. */
    @Test void testBigNumberRoots() {
        assertEquals(new BigInteger("7"), binding(StreamingContext.EMPTY).readNode("7", BigInteger.class));
        assertEquals(new BigDecimal("8.25"), binding(StreamingContext.EMPTY).readNode("8.25", BigDecimal.class));
    }
    static class Primitives { public boolean booleanValue = true; public byte byteValue = 1; public char charValue = 'x'; public short shortValue = 1; public int intValue = 1; public long longValue = 1; public float floatValue = 1; public double doubleValue = 1; }
    static class Wrappers { public Integer value = 1; }
}
