package org.sjf4j.binding.contract;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.exception.BindingException;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Portable scalar cases from Jackson's JDKScalarsDeserTest. */
public abstract class JDKScalarsDeserializationContract {
    protected abstract JsonBinder<?, ?> binding(RuntimeContext context);
    /** Source: JDKScalarsDeserTest#testBooleanWrapper. */
    @Test void testBooleanWrapper() {
        assertEquals(Boolean.TRUE, binding(RuntimeContext.EMPTY).readNode("true", Boolean.class));
        assertEquals(Boolean.FALSE, binding(RuntimeContext.EMPTY).readNode("false", Boolean.class));
    }
    /** Source: JDKScalarsDeserTest#testCharacterWrapper (one-character-string case). */
    @Test void testCharacterWrapper() {
            assertEquals('x', binding(RuntimeContext.EMPTY).readNode("\"x\"", Character.class));
        }
    /** Source: JDKScalarsDeserTest#testIntWrapper and #testLongWrapper (native numeric input). */
    @Test void testIntegerAndLongWrapper() {
        assertEquals(-42, binding(RuntimeContext.EMPTY).readNode("-42", Integer.class));
        assertEquals(12345678901L, binding(RuntimeContext.EMPTY).readNode("12345678901", Long.class));
    }
    /** Explicit JSON null for a primitive is rejected by SJF4J. */
    @Test void testNullForPrimitivePropertiesIsRejected() {
        assertThrows(BindingException.class, () -> binding(RuntimeContext.EMPTY).readNode("{\"booleanValue\":null}", Primitives.class));
    }
    /** Explicit JSON null for a primitive array element is rejected by SJF4J. */
    @Test void testNullForPrimitiveArrayElementsIsRejected() {
        assertThrows(BindingException.class, () -> binding(RuntimeContext.EMPTY).readNode("[null]", int[].class));
    }
    /** Source: JDKScalarsDeserTest#testNullForPrimitivesDefault (reference null contrast). */
    @Test void testNullForWrapperProperty() {
            assertNull(((Wrappers) binding(RuntimeContext.EMPTY).readNode("{\"value\":null}", Wrappers.class)).value);
        }
    /** Retained SJF4J scalar-root coverage; JDKScalarsDeserTest has no successful BigInteger/BigDecimal root counterpart. */
    @Test void testBigNumberRoots() {
        assertEquals(new BigInteger("7"), binding(RuntimeContext.EMPTY).readNode("7", BigInteger.class));
        assertEquals(new BigDecimal("8.25"), binding(RuntimeContext.EMPTY).readNode("8.25", BigDecimal.class));
    }
    static class Primitives { public boolean booleanValue = true; public byte byteValue = 1; public char charValue = 'x'; public short shortValue = 1; public int intValue = 1; public long longValue = 1; public float floatValue = 1; public double doubleValue = 1; }
    static class Wrappers { public Integer value = 1; }
}
