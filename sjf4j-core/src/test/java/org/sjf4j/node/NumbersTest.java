package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumbersTest {

    @Test
    void testIntegralAndFloatingConversions() {
        assertTrue(Numbers.isIntegralType(1));
        assertTrue(Numbers.isIntegralType(BigInteger.TEN));
        assertFalse(Numbers.isIntegralType(1.5d));

        assertEquals(12L, Numbers.toLong(12L));
        assertEquals(12L, Numbers.toLong(12));
        assertEquals(12L, Numbers.toLong(new BigInteger("12")));
        assertEquals(12L, Numbers.toLong(new BigDecimal("12")));
        assertThrows(JsonException.class, () -> Numbers.toLong(Double.MAX_VALUE));
        assertThrows(JsonException.class, () -> Numbers.toLong(new BigInteger("9223372036854775808")));
        assertThrows(JsonException.class, () -> Numbers.toLong(new BigDecimal("9223372036854775808")));

        assertEquals(12, Numbers.toInt(12L));
        assertThrows(JsonException.class, () -> Numbers.toInt(((long) Integer.MAX_VALUE) + 1));

        assertEquals((short) 12, Numbers.toShort(12));
        assertThrows(JsonException.class, () -> Numbers.toShort(Short.MAX_VALUE + 1L));

        assertEquals((byte) 12, Numbers.toByte(12));
        assertThrows(JsonException.class, () -> Numbers.toByte(Byte.MAX_VALUE + 1L));

        assertEquals(12.0d, Numbers.toDouble(12d));
        assertEquals(12.0d, Numbers.toDouble(12));
        assertThrows(JsonException.class, () -> Numbers.toDouble(Float.POSITIVE_INFINITY));

        assertEquals(12.0f, Numbers.toFloat(12f));
        assertEquals(12.0f, Numbers.toFloat(12));
        assertThrows(JsonException.class, () -> Numbers.toFloat(Double.MAX_VALUE));
    }

    @Test
    void testBigConversionsAndTypedDispatch() {
        assertEquals(BigInteger.TEN, Numbers.toBigInteger(BigInteger.TEN));
        assertEquals(BigInteger.TEN, Numbers.toBigInteger(new BigDecimal("10.9")));
        assertEquals(BigInteger.valueOf(12), Numbers.toBigInteger(12.8d));
        assertThrows(JsonException.class, () -> Numbers.toBigInteger(Double.NEGATIVE_INFINITY));

        assertEquals(new BigDecimal("10.5"), Numbers.toBigDecimal(new BigDecimal("10.5")));
        assertEquals(new BigDecimal("10"), Numbers.toBigDecimal(BigInteger.TEN));
        assertEquals(new BigDecimal("1.5"), Numbers.toBigDecimal(1.5d));
        assertEquals(new BigDecimal("12"), Numbers.toBigDecimal(12));

        Integer same = 7;
        assertSame(same, Numbers.to(same, Integer.class));
        assertSame(same, Numbers.to(same, null));
        assertEquals(7L, Numbers.to(7, Long.class));
        assertEquals(7, Numbers.to(7L, Integer.class));
        assertEquals((short) 7, Numbers.to(7, Short.class));
        assertEquals((byte) 7, Numbers.to(7, Byte.class));
        assertEquals(7.0d, Numbers.to(7, Double.class));
        assertEquals(7.0f, Numbers.to(7, Float.class));
        assertEquals(BigInteger.valueOf(7), Numbers.to(7, BigInteger.class));
        assertEquals(BigDecimal.valueOf(7), Numbers.to(7, BigDecimal.class));
        assertThrows(JsonException.class, () -> Numbers.to(7, Boolean.class));
    }

    @Test
    void testParseNumberAndIsNumeric() {
        assertEquals(12, Numbers.parseNumber("12"));
        assertEquals(12, Numbers.parseNumber(" 1_2 "));
        assertEquals(12345678901L, Numbers.parseNumber("12345678901"));
        assertEquals(new BigInteger("9999999999999999999"), Numbers.parseNumber("9999999999999999999"));
        assertEquals(1.5d, Numbers.parseNumber("1.5"));
        assertEquals(Double.POSITIVE_INFINITY, Numbers.parseNumber("1e9999"));
        assertThrows(JsonException.class, () -> Numbers.parseNumber(null));
        assertThrows(JsonException.class, () -> Numbers.parseNumber("   "));
        assertThrows(JsonException.class, () -> Numbers.parseNumber("12x"));
        assertThrows(JsonException.class, () -> Numbers.parseNumber("1".repeat(101)));

        assertFalse(Numbers.isNumeric(null));
        assertFalse(Numbers.isNumeric(""));
        assertFalse(Numbers.isNumeric("   "));
        assertTrue(Numbers.isNumeric("12"));
        assertTrue(Numbers.isNumeric("1_2"));
        assertTrue(Numbers.isNumeric("-1.2e+3"));
        assertFalse(Numbers.isNumeric("+"));
        assertFalse(Numbers.isNumeric("1..2"));
        assertFalse(Numbers.isNumeric("1e"));
        assertFalse(Numbers.isNumeric("1e-2-3"));
        assertFalse(Numbers.isNumeric("1a"));
    }

    @Test
    void testParseDoubleLiteral() {
        int[] pos1 = {0};
        assertEquals(12.0d, Numbers.parseDoubleLiteral("12", pos1));
        assertEquals(2, pos1[0]);

        int[] pos2 = {0};
        assertEquals(-10.25d, Numbers.parseDoubleLiteral("-10.25]", pos2));
        assertEquals(6, pos2[0]);

        int[] pos3 = {2};
        assertEquals(7.5d, Numbers.parseDoubleLiteral("x 7.5,", pos3));
        assertEquals(5, pos3[0]);

        int[] pos4 = {0};
        assertEquals(12.0d, Numbers.parseDoubleLiteral("12.", pos4));
        assertEquals(3, pos4[0]);

        int[] pos5 = {0};
        assertEquals(0.5d, Numbers.parseDoubleLiteral(".5", pos5));
        assertEquals(2, pos5[0]);

        int[] pos6 = {0};
        assertEquals(1.0d, Numbers.parseDoubleLiteral("1e2", pos6));
        assertEquals(1, pos6[0]);

        assertThrows(NullPointerException.class, () -> Numbers.parseDoubleLiteral(null, new int[]{0}));
        assertThrows(NullPointerException.class, () -> Numbers.parseDoubleLiteral("1", null));
        assertThrows(NumberFormatException.class, () -> Numbers.parseDoubleLiteral("-", new int[]{0}));
        assertThrows(NumberFormatException.class, () -> Numbers.parseDoubleLiteral("+1", new int[]{0}));
    }

    @Test
    void testSemanticIntegerComparisonAndHashing() {
        assertFalse(Numbers.isSemanticInteger(null));
        assertTrue(Numbers.isSemanticInteger(12));
        assertTrue(Numbers.isSemanticInteger(new BigDecimal("12.0")));
        assertFalse(Numbers.isSemanticInteger(new BigDecimal("12.5")));
        assertTrue(Numbers.isSemanticInteger(12.0d));
        assertFalse(Numbers.isSemanticInteger(Double.POSITIVE_INFINITY));

        assertEquals(new BigDecimal("12.3"), Numbers.normalizeDecimal(new BigDecimal("12.300")));
        assertEquals(new BigDecimal("12.5"), Numbers.normalizeDecimal(12.5d));

        assertTrue(Numbers.compare(new BigInteger("12"), 11L) > 0);
        assertEquals(0, Numbers.compare(12, 12L));
        assertEquals(0, Numbers.compare(new BigDecimal("1.50"), 1.5d));

        assertEquals(Long.hashCode(12L), Numbers.hash(12));
        assertEquals(Double.hashCode(Double.NaN), Numbers.hash(Double.NaN));
        assertEquals(Long.hashCode(12L), Numbers.hash(12.0d));
        assertEquals(BigInteger.TEN.hashCode(), Numbers.hash(BigInteger.TEN));
        assertEquals(new BigDecimal("1.5").hashCode(), Numbers.hash(new BigDecimal("1.50")));

        Number custom = new Number() {
            @Override
            public int intValue() {
                return 3;
            }

            @Override
            public long longValue() {
                return 3L;
            }

            @Override
            public float floatValue() {
                return 3.5f;
            }

            @Override
            public double doubleValue() {
                return 3.5d;
            }

            @Override
            public String toString() {
                return "3.5";
            }
        };
        assertEquals(new BigDecimal("3.5").hashCode(), Numbers.hash(custom));
    }
}
