package org.sjf4j.util;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;
import org.sjf4j.util.Numbers;

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
        assertThrows(ArithmeticException.class, () -> Numbers.toLong(Double.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> Numbers.toLong(0x1.0p63));
        assertThrows(ArithmeticException.class, () -> Numbers.toLong(new BigInteger("9223372036854775808")));
        assertThrows(ArithmeticException.class, () -> Numbers.toLong(new BigDecimal("9223372036854775808")));

        assertEquals(12, Numbers.toInt(12L));
        assertThrows(ArithmeticException.class, () -> Numbers.toInt(((long) Integer.MAX_VALUE) + 1));

        assertEquals((short) 12, Numbers.toShort(12));
        assertEquals(Short.MIN_VALUE, Numbers.toShort((long) Short.MIN_VALUE));
        assertEquals(Short.MAX_VALUE, Numbers.toShort((long) Short.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> Numbers.toShort(Short.MAX_VALUE + 1L));
        assertThrows(ArithmeticException.class, () -> Numbers.toShort((long) Short.MIN_VALUE - 1));

        assertEquals((byte) 12, Numbers.toByte(12));
        assertThrows(ArithmeticException.class, () -> Numbers.toByte(Byte.MAX_VALUE + 1L));

        assertEquals(12.0d, Numbers.toDouble(12d));
        assertEquals(12.0d, Numbers.toDouble(12));
        assertThrows(ArithmeticException.class, () -> Numbers.toDouble(Float.POSITIVE_INFINITY));

        assertEquals(12.0f, Numbers.toFloat(12f));
        assertEquals(12.0f, Numbers.toFloat(12));
        assertThrows(ArithmeticException.class, () -> Numbers.toFloat(Double.MAX_VALUE));
        assertEquals(Float.MAX_VALUE, Numbers.toFloat((double) Float.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> Numbers.toFloat(Double.POSITIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> Numbers.toFloat(1e50d));
    }

    @Test
    void testBigConversionsAndTypedDispatch() {
        assertEquals(BigInteger.TEN, Numbers.toBigInteger(BigInteger.TEN));
        assertEquals(BigInteger.TEN, Numbers.toBigInteger(new BigDecimal("10.9")));
        assertEquals(BigInteger.valueOf(12), Numbers.toBigInteger(12.8d));
        assertEquals(BigDecimal.valueOf(1e100d).toBigInteger(), Numbers.toBigInteger(1e100d));
        assertThrows(ArithmeticException.class, () -> Numbers.toBigInteger(Double.NEGATIVE_INFINITY));

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
        assertThrows(NodeException.class, () -> Numbers.to(7, Boolean.class));
    }

    @Test
    void testLenientFractionalNarrowingRemainsUnchanged() {
        assertEquals(127, Numbers.toByte(new BigDecimal("127.5")));
        assertEquals(-128, Numbers.toByte(new BigDecimal("-128.5")));
        assertEquals(32767, Numbers.toShort(new BigDecimal("32767.5")));
        assertEquals(-32768, Numbers.toShort(new BigDecimal("-32768.5")));
        assertEquals(Integer.MAX_VALUE, Numbers.toInt(new BigDecimal("2147483647.5")));
        assertEquals(Integer.MIN_VALUE, Numbers.toInt(new BigDecimal("-2147483648.5")));
        assertEquals(Integer.MAX_VALUE, Numbers.toInt(2147483647.5d));
        assertEquals(1, Numbers.toInt(new BigDecimal("1.9")));
        // Integer narrowing still rejects values whose truncated result is outside the target.
        assertThrows(ArithmeticException.class, () -> Numbers.toByte(new BigDecimal("128.0")));
        assertThrows(ArithmeticException.class, () -> Numbers.toInt(new BigDecimal("2147483648.0")));
    }

    @Test
    void testParseNumberAndIsNumeric() {
        assertEquals(12, Numbers.parseNumber("12"));
//        assertEquals(123, Numbers.parseNumber(" \t1_2_3\r"));
//        assertEquals(123, Numbers.parseNumber("__ \t1_2_3_ \n__"));
        assertEquals(12345678901L, Numbers.parseNumber("12345678901"));
        assertEquals(new BigInteger("9999999999999999999"), Numbers.parseNumber("9999999999999999999"));
        assertEquals(1.5d, Numbers.parseNumber("1.5"));
        assertEquals(new BigDecimal("1e9999"), Numbers.parseNumber("1e9999"));
        assertThrows(NumberFormatException.class, () -> Numbers.parseNumber(null));
        assertThrows(NumberFormatException.class, () -> Numbers.parseNumber("   "));
        assertThrows(NumberFormatException.class, () -> Numbers.parseNumber("12x"));
        assertThrows(NumberFormatException.class, () -> Numbers.parseNumber("1".repeat(101)));

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
    void testParseNumberExactIntegralTypesAndBoundaries() {
        String[] literals = {
                "0", "-0", "1", "-1", "2147483647", "-2147483648",
                "2147483648", "-2147483649", "9223372036854775807",
                "-9223372036854775808", "9223372036854775808",
                "-9223372036854775809", "99999999999999999999999999999"
        };
        for (String literal : literals) {
            Number expected = Numbers.parseNumber(literal);
            Number actual = Numbers.parseNumberExact(literal);
            assertEquals(expected.getClass(), actual.getClass(), literal);
            assertEquals(expected, actual, literal);
        }
        assertEquals(Integer.MAX_VALUE, Numbers.parseNumberExact("2147483647"));
        assertEquals(Integer.MIN_VALUE, Numbers.parseNumberExact("-2147483648"));
        assertEquals(Long.MAX_VALUE, Numbers.parseNumberExact("9223372036854775807"));
        assertEquals(Long.MIN_VALUE, Numbers.parseNumberExact("-9223372036854775808"));
        assertEquals(new BigInteger("9223372036854775808"),
                Numbers.parseNumberExact("9223372036854775808"));
        assertEquals(new BigInteger("-9223372036854775809"),
                Numbers.parseNumberExact("-9223372036854775809"));
    }

    @Test
    void testParseNumberExactDecimalsAndExponents() {
        for (String literal : new String[]{
                "0.1", "-0.1", "1.0", "1.2500", "-0.00",
                "0.123456789123456789", "9007199254740993.0",
                "1e3", "1E+3", "-1.25e-2", "1e-400", "1e400",
                "1e-9999", "1e9999"
        }) {
            Number actual = Numbers.parseNumberExact(literal);
            assertEquals(BigDecimal.class, actual.getClass(), literal);
            assertEquals(new BigDecimal(literal), actual, literal);
        }
        assertEquals(0.1d, Numbers.parseNumber("0.1"));
        assertEquals(1000.0d, Numbers.parseNumber("1e3"));
        assertEquals(0.0d, Numbers.parseNumber("1e-400"));
        assertEquals(new BigDecimal("1e400"), Numbers.parseNumber("1e400"));
    }

    @Test
    void testParseNumberExactHasNoHistoricalLengthLimit() {
        String integer = "9".repeat(150);
        String decimal = "0." + "1234567890".repeat(15);
        assertEquals(new BigInteger(integer), Numbers.parseNumberExact(integer));
        assertEquals(new BigInteger("-" + integer), Numbers.parseNumberExact("-" + integer));
        assertEquals(new BigDecimal(decimal), Numbers.parseNumberExact(decimal));
        assertEquals(new BigDecimal(decimal + "e-500"),
                Numbers.parseNumberExact(decimal + "e-500"));

        String atLimit = "9".repeat(100);
        assertEquals(new BigInteger(atLimit), Numbers.parseNumber(atLimit));
        assertThrows(NumberFormatException.class, () -> Numbers.parseNumber(integer));
        assertThrows(NumberFormatException.class, () -> Numbers.parseNumber(decimal));
    }

    @Test
    void testParseNumberExactRejectsInvalidLiterals() {
        for (String literal : new String[]{
                null, "", " ", "-", "NaN", "Infinity", "-Infinity", "12x",
                "1.2.3", "1e", "1e+", "1.0d", "1.0f", "1.0 ",
                "1e2147483648", "1e-2147483648"
        }) {
            assertThrows(NumberFormatException.class, () -> Numbers.parseNumberExact(literal),
                    String.valueOf(literal));
        }
    }

//    @Test
//    void testParseNumberTypeAndRangeAfterNormalization() {
//        assertEquals(Integer.class, Numbers.parseNumber(" 2147483647 ").getClass());
//        assertEquals(Long.class, Numbers.parseNumber("_2147483648_").getClass());
//        assertEquals(Long.class, Numbers.parseNumber("_9223372036854775807_").getClass());
//        assertEquals(BigInteger.class, Numbers.parseNumber("_9223372036854775808_").getClass());
//        assertEquals(Double.class, Numbers.parseNumber("_1.25e2_").getClass());
//
//        assertEquals(BigInteger.class, Numbers.parseNumber("_" + "1".repeat(100) + "_").getClass());
//        assertThrows(NodeException.class, () -> Numbers.parseNumber("_" + "1".repeat(101) + "_"));
//    }

    @Test
    void testExactMultipleOfFastPathMatchesDecimalRemainder() {
        Number[] values = {
                (byte) 6, (short) -6, 7, 6L, Long.MIN_VALUE, Long.MAX_VALUE,
                BigInteger.valueOf(Long.MIN_VALUE), BigInteger.ONE.shiftLeft(64),
                new BigDecimal("6.000"), new BigDecimal("6.000000000000000001"),
                0.0d, -0.0f, 12.0d, -12.0f, 12.5d, -12.5f, 0.3d, 0.3f,
                Double.MIN_VALUE, Float.MIN_VALUE,
                Math.nextDown(0x1.0p53), 0x1.0p53, Math.nextUp(0x1.0p53),
                -0x1.0p53, Math.nextDown(-0x1.0p53),
                Math.nextDown(0x1.0p24f), 0x1.0p24f, Math.nextUp(0x1.0p24f),
                -0x1.0p24f, Math.nextDown(-0x1.0p24f),
                1000000064f, 1000000000000000128d, 1e20d, -3e20d
        };
        for (long divisor : new long[]{1, 2, 3, 7, 10, Long.MAX_VALUE}) {
            BigDecimal decimal = BigDecimal.valueOf(divisor);
            for (Number value : values) {
                boolean expected = new BigDecimal(value.toString()).remainder(decimal).signum() == 0;
                assertEquals(expected, Numbers.isMultipleOf(value, decimal, divisor),
                        value.getClass().getSimpleName() + " " + value + " / " + divisor);
            }
        }
        BigDecimal tenth = new BigDecimal("0.1");
        assertTrue(Numbers.isMultipleOf(0.3f, tenth, 0L));
        assertTrue(Numbers.isMultipleOf(0.3d, tenth, 0L));
        assertFalse(Numbers.isMultipleOf(new BigDecimal("0.300000000000000001"), tenth, 0L));
        BigDecimal huge = new BigDecimal("18446744073709551616");
        assertTrue(Numbers.isMultipleOf(BigInteger.ONE.shiftLeft(64), huge, 0L));
        assertFalse(Numbers.isMultipleOf(Long.MAX_VALUE, huge, 0L));
    }

    @Test
    void testExactNumericOrderingAndHashContract() {
        BigInteger max = new BigInteger("18446744073709551615");
        assertTrue(Numbers.compare(max, max.add(BigInteger.ONE)) < 0);
        assertTrue(Numbers.compare(max.negate(), max.negate().subtract(BigInteger.ONE)) > 0);
        assertTrue(Numbers.compare(9007199254740993L, 9007199254740992d) > 0);
        assertTrue(Numbers.compare(new BigDecimal("0.123456789123456789"),
                new BigDecimal("0.123456789123456790")) < 0);
        assertTrue(Numbers.compare(new BigDecimal("1e-400"), 0d) > 0);
        assertTrue(Numbers.compare(new BigDecimal("1e400"), Double.MAX_VALUE) > 0);

        Number[][] equalGroups = {
                {0, -0.0d, -0.0f, BigInteger.ZERO, new BigDecimal("0.000")},
                {1, 1L, 1.0f, 1.0d, BigInteger.ONE, new BigDecimal("1.000")},
                {0.1f, 0.1d, new BigDecimal("0.100")},
                {1e18d, 1000000000000000000L, new BigDecimal("1e18")},
                {1e20d, new BigInteger("100000000000000000000"), new BigDecimal("1e20")},
                {new BigDecimal(Double.toString((double) Long.MIN_VALUE)), (double) Long.MIN_VALUE},
                {new BigDecimal(Float.toString(1000000064f)), 1000000064f},
                {new BigDecimal(Double.toString(1000000000000000128d)), 1000000000000000128d}
        };
        for (Number[] group : equalGroups) {
            for (Number left : group) {
                for (Number right : group) {
                    assertEquals(0, Numbers.compare(left, right), left + " vs " + right);
                    assertEquals(Numbers.hash(left), Numbers.hash(right), left + " vs " + right);
                }
            }
        }
        // A widened Float is a different decimal from the original Float.
        assertTrue(Numbers.compare(0.1f, (double) 0.1f) < 0);
        assertTrue(Numbers.compare(16777217L, 16777216f) > 0);
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
        assertTrue(Numbers.compare(BigInteger.ONE, new BigDecimal("1.5")) < 0);

        assertEquals(Numbers.hash(12L), Numbers.hash(12));
        assertEquals(Double.hashCode(Double.NaN), Numbers.hash(Double.NaN));
        assertEquals(Numbers.hash(12L), Numbers.hash(12.0d));
        assertEquals(Numbers.hash(BigInteger.TEN), Numbers.hash(new BigDecimal("10.0")));
        assertEquals(Numbers.hash(new BigDecimal("1.5")), Numbers.hash(new BigDecimal("1.50")));

        long[] integralValues = {Long.MIN_VALUE, -1_000_000L, -100L, -1L, 0L, 1L, 100L,
                1_000_000L, Long.MAX_VALUE};
        for (long value : integralValues) {
            int expected = Long.hashCode(value);
            assertEquals(expected, Numbers.hash(value));
            assertEquals(expected, Numbers.hash(BigInteger.valueOf(value)));
            assertEquals(expected, Numbers.hash(new BigDecimal(value + ".0")));
            double floating = (double) value;
            // Compare/hash the decimal representation, not the result of a
            // narrowing cast at the edges of the long range.
            BigDecimal decimal = new BigDecimal(Double.toString(floating));
            assertEquals(0, Numbers.compare(floating, decimal));
            assertEquals(Numbers.hash(decimal), Numbers.hash(floating));
        }

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
