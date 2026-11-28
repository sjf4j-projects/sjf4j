package org.sjf4j.util;

import org.sjf4j.exception.NodeException;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Numeric conversion helpers with range checks.
 */
public final class Numbers {

    /**
     * Historical length limit for {@link #parseNumber(String)}.
     * Exact parsing does not impose this limit; input size restrictions are
     * the responsibility of the caller or backend.
     */
    private final static int MAX_NUMBER_LENGTH = 100;
    
    /**
     * BigInteger representation of Long.MIN_VALUE.
     */
    private final static BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    
    /**
     * BigInteger representation of Long.MAX_VALUE.
     */
    private final static BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    
    /**
     * BigDecimal representation of Long.MIN_VALUE.
     */
    private final static BigDecimal BD_MIN_LONG = BigDecimal.valueOf(Long.MIN_VALUE);
    
    /**
     * BigDecimal representation of Long.MAX_VALUE.
     */
    private final static BigDecimal BD_MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);

    /**
     * Checks if a BigInteger number is within the range of a Long.
     */
    private static boolean inLongRange(BigInteger number) {
        Asserts.notNull(number, "number");
        return (number.compareTo(BI_MIN_LONG) >= 0) && (number.compareTo(BI_MAX_LONG) <= 0);
    }

    /**
     * Checks if a BigDecimal number is within the range of a Long.
     */
    private static boolean inLongRange(BigDecimal number) {
        Asserts.notNull(number, "number");
        return (number.compareTo(BD_MIN_LONG) >= 0) && (number.compareTo(BD_MAX_LONG) <= 0);
    }

    /**
     * Checks if a double number is within the range of a Long.
     *
     * @param number the double number to check
     * @return true if the number is within Long range, false otherwise
     */
    private static boolean inLongRange(double number) {
        return Double.isFinite(number) && number >= Long.MIN_VALUE && number < 0x1.0p63;
    }


    /**
     * Checks if a Number is an integral type (Byte, Short, Integer, Long, or BigInteger).
     *
     * @param number the Number to check
     * @return true if the Number is an integral type, false otherwise
     */
    public static boolean isIntegralType(Number number) {
        return number instanceof Byte || number instanceof Short || number instanceof Integer ||
                number instanceof Long || number instanceof BigInteger;
    }

    /**
     * Checks if a Number is a floating point type (Float or Double).
     *
     * @param number the Number to check
     * @return true if the Number is a floating point type, false otherwise
     */
    private static boolean isFloatingType(Number number) {
        return number instanceof Float || number instanceof Double;
    }

    /**
     * Converts a Number to a Long with range checking.
     */
    public static long toLong(Number number) {
        Asserts.notNull(number, "number");
        if (number instanceof Long) return (long) number;
        if ((number instanceof Double || number instanceof Float) && !inLongRange(number.doubleValue())) {
            throw new ArithmeticException("cannot convert floating-point Number '" + number + "' to Long: out of 64-bit range");
        }
        if (number instanceof BigInteger && !inLongRange((BigInteger) number)) {
            throw new ArithmeticException("cannot convert BigInteger '" + number + "' to Long: out of 64-bit range");
        }
        if (number instanceof BigDecimal && !inLongRange((BigDecimal) number)) {
            throw new ArithmeticException("cannot convert BigDecimal '" + number + "' to Long: out of 64-bit range");
        }
        return number.longValue();
    }

    /**
     * Converts a Number to an Integer with range checking.
     */
    public static int toInt(Number number) {
        long longValue = toLong(number);
        return toInt(longValue);
    }

    /**
     * Converts a long to an int with range checking.
     */
    public static int toInt(long longValue) {
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new ArithmeticException("cannot convert long '" + longValue + "' to int: out of 32-bit range");
        }
        return (int) longValue;
    }

    /**
     * Converts a Number to a short with range checking.
     */
    public static short toShort(Number number) {
        long longValue = toLong(number);
        return toShort(longValue);
    }

    /**
     * Converts a long to a short with range checking.
     */
    public static short toShort(long longValue) {
        if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
            throw new ArithmeticException("cannot convert long '" + longValue + "' to short: out of 16-bit range");
        }
        return (short) longValue;
    }

    /**
     * Converts a Number to a byte with range checking.
     */
    public static byte toByte(Number number) {
        long longValue = toLong(number);
        return toByte(longValue);
    }

    /**
     * Converts a long to a byte with range checking.
     */
    public static byte toByte(long longValue) {
        if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
            throw new ArithmeticException("cannot convert long '" + longValue + "' to byte: out of 8-bit range");
        }
        return (byte) longValue;
    }

    /**
     * Converts a Number to a Double with range checking.
     */
    public static double toDouble(Number number) {
        Asserts.notNull(number, "number");
        if (number instanceof Double) return (double) number;
        double d = number.doubleValue();
        if (!Double.isFinite(d)) {
            throw new ArithmeticException("cannot convert Number '" + number + "' to double: non-finite value");
        }
        return d;
    }

    /**
     * Converts a Number to a Float with range checking.
     */
    public static float toFloat(Number number) {
        Asserts.notNull(number, "number");
        if (number instanceof Float) return (float) number;
        float f = number.floatValue();
        if (!Float.isFinite(f)) {
            throw new ArithmeticException("cannot convert Number '" + number + "' to float: non-finite value");
        }
        return f;
    }

    /**
     * Converts a double to a finite float with range checking.
     */
    public static float toFloat(double doubleValue) {
        float floatValue = (float) doubleValue;
        if (!Float.isFinite(floatValue)) {
            throw new ArithmeticException("cannot convert double '" + doubleValue + "' to float: non-finite value");
        }
        return floatValue;
    }

    /**
     * Converts a Number to BigInteger.
     */
    public static BigInteger toBigInteger(Number number) {
        Asserts.notNull(number, "number");
        if (number instanceof BigInteger) return (BigInteger) number;
        if (number instanceof BigDecimal) return ((BigDecimal) number).toBigInteger();
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                throw new ArithmeticException("cannot convert non-finite floating-point '" + number + "' to BigInteger");
            }
            return BigDecimal.valueOf(d).toBigInteger();
        }
        return BigInteger.valueOf(number.longValue());
    }

    /**
     * Converts a Number to BigDecimal.
     */
    public static BigDecimal toBigDecimal(Number number) {
        Asserts.notNull(number, "number");
        if (number instanceof BigDecimal) return (BigDecimal) number;
        if (number instanceof BigInteger) return new BigDecimal((BigInteger) number);
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(number.toString());
    }

    /**
     * Converts a Number to the requested numeric target type.
     *
     * <p>Fractional parts and floating-point precision may be lost. The
     * existing integral range checks still apply.</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> T to(Number number, Class<T> clazz) {
        Asserts.notNull(number, "number");
        if (clazz == null || clazz.isAssignableFrom(number.getClass())) return (T) number;
        Class<?> boxed = Types.box(clazz);
        if (boxed == Long.class) return (T) Long.valueOf(Numbers.toLong(number));
        if (boxed == Integer.class) return (T) Integer.valueOf(Numbers.toInt(number));
        if (boxed == Short.class) return (T) Short.valueOf(Numbers.toShort(number));
        if (boxed == Byte.class) return (T) Byte.valueOf(Numbers.toByte(number));
        if (boxed == Double.class) return (T) Double.valueOf(Numbers.toDouble(number));
        if (boxed == Float.class) return (T) Float.valueOf(Numbers.toFloat(number));
        if (boxed == BigInteger.class) return (T) Numbers.toBigInteger(number);
        if (boxed == BigDecimal.class) return (T) Numbers.toBigDecimal(number);
        throw new NodeException("cannot convert " + Types.name(number) + " '" + number + "' to " + clazz.getName());
    }


    /**
     * Parses a numeric literal into the smallest practical representation.
     *
     * <p>Integral literals are returned in this order: {@link Integer},
     * {@link Long}, then {@link BigInteger}. Decimal or exponent literals are
     * returned as a finite {@link Double}; when the literal overflows
     * {@code double}, it is preserved as a {@link BigDecimal} instead.</p>
     *
     * @param text numeric literal text
     * @return an {@code Integer}, {@code Long}, {@code BigInteger},
     *         {@code Double}, or {@code BigDecimal}, according to the literal
     *         form and range
     * @throws NumberFormatException if the text is empty, too long, or invalid
     */
    public static Number parseNumber(String text) {
        return _parseNumber(text, false);
    }

    /**
     * Parses a numeric literal without rounding or loss of range.
     *
     * <p>Integral literals follow the same representation selection as
     * {@link #parseNumber(String)}: {@link Integer}, {@link Long}, or
     * {@link BigInteger}. Literals with a decimal point or exponent are always
     * parsed directly as {@link BigDecimal}, even when the value is integral.
     * No intermediate floating-point conversion is performed.</p>
     *
     * <p>Unlike {@link #parseNumber(String)}, this method does not impose
     * the historical 100-character length limit. Callers and backends remain
     * responsible for input size limits. Decimal exponents and scales must
     * still be representable by {@link BigDecimal}.</p>
     *
     * @param text numeric literal text
     * @return an {@code Integer}, {@code Long}, {@code BigInteger},
     *         or {@code BigDecimal}, according to the literal form and range
     * @throws NumberFormatException if the text is empty or invalid, or the
     *         decimal exponent or scale is out of range
     */
    public static Number parseNumberExact(String text) {
        return _parseNumber(text, true);
    }

    private static Number _parseNumber(String text, boolean exactNumbers) {
        if (text == null || text.isEmpty()) {
            throw new NumberFormatException("invalid number text: value is null or empty");
        }

        final int len = text.length();
        if (!exactNumbers && len > MAX_NUMBER_LENGTH) {
            throw new NumberFormatException("invalid number text: too large (" + len + " chars): '" +
                    text + "'");
        }

        int i = 0;
        final boolean negative = text.charAt(0) == '-';
        if (negative) {
            if (++i == len) {
                throw new NumberFormatException("invalid number text: '" + text + "'");
            }
        }

        /*
         * Use negative accumulation, same basic technique as Long.parseLong().
         *
         * This allows Long.MIN_VALUE (-9223372036854775808) to be represented
         * without overflowing during parsing.
         */
        final long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        final long multMin = limit / 10;

        long result = 0;
        int digits = 0;
        boolean overflow = false;

        for (; i < len; i++) {
            final char c = text.charAt(i);
            final int digit = c - '0';
            if (digit >= 0 && digit <= 9) {
                digits++;

                // Preserve the existing semantic:
                // more than 19 integer digits -> BigInteger.
                if (digits > 19) {
                    overflow = true;
                    continue;
                }
                if (!overflow) {
                    if (result < multMin) {
                        overflow = true;
                        continue;
                    }
                    result *= 10;
                    if (result < limit + digit) {
                        overflow = true;
                        continue;
                    }
                    result -= digit;
                }
                continue;
            }

            // Decimal or exponent literal: delegate syntax validation to the JDK parser.
            if (c == '.' || c == 'e' || c == 'E') {
                return exactNumbers ? new BigDecimal(text) : _parseFloatingNumber(text);
            }

            throw new NumberFormatException("invalid number text: '" + text + "'");
        }

        if (digits == 0) {
            throw new NumberFormatException("invalid number text: '" + text + "'");
        }

        if (overflow) {
            return new BigInteger(text);
        }

        final long value = negative ? result : -result;
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return (int) value;
        }
        return value;
    }

    private static Number _parseFloatingNumber(String text) {
        final double value = Double.parseDouble(text);
        if (Double.isFinite(value)) {
            return value;
        }

        // Double overflow (e.g. 1e10000):
        // preserve the finite numeric value using BigDecimal.
        return new BigDecimal(text);
    }



    /**
     * Returns true when the text is a valid numeric literal.
     */
    public static boolean isNumeric(String text) {
        if (text == null || text.isEmpty()) return false;
        text = text.replace("_", "").trim();

        boolean dotSeen = false, eSeen = false, digitSeen = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '+' || c == '-') {
                if (i > 0 && text.charAt(i - 1) != 'e' && text.charAt(i - 1) != 'E') return false;
            } else if (c == '.') {
                if (dotSeen || eSeen) return false;
                dotSeen = true;
            } else if (c == 'e' || c == 'E') {
                if (eSeen || !digitSeen || i == text.length() - 1) return false;
                eSeen = true;
            } else if (Character.isDigit(c)) {
                digitSeen = true;
            } else {
                return false;
            }
        }
        return digitSeen;
    }

    /**
     * Returns true when the number is mathematically an integer.
     */
    public static boolean isSemanticInteger(Number number) {
        if (number == null) return false;
        if (isIntegralType(number)) return true;
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).stripTrailingZeros().scale() <= 0;
        }
        double d = number.doubleValue();
        return d % 1 == 0 && Double.isFinite(d);
    }

    /**
     * Normalizes a number to stripped BigDecimal form.
     */
    public static BigDecimal normalizeDecimal(Number number) {
        return semanticDecimal(number).stripTrailingZeros();
    }

    // Use the number's decimal representation consistently for comparison,
    // hashing and remainder. In particular, do not widen Float to Double:
    // 0.1f is represented as "0.1", not "0.10000000149011612".
    private static BigDecimal semanticDecimal(Number number) {
        if (number instanceof BigDecimal) return (BigDecimal) number;
        if (number instanceof BigInteger) return new BigDecimal((BigInteger) number);
        if (isIntegralType(number)) return BigDecimal.valueOf(number.longValue());
        if (number instanceof Double) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(number.toString());
    }

    /**
     * Tests divisibility using decimal numeric semantics.
     *
     * @param number finite numeric value
     * @param divisor positive decimal divisor
     * @param divisorLong exact positive long value of divisor, or zero if it
     *                    is fractional or outside the long range
     */
    public static boolean isMultipleOf(Number number, BigDecimal divisor, long divisorLong) {
        if (divisorLong > 0L) {
            if (isIntegralType(number)
                    && (!(number instanceof BigInteger) || ((BigInteger) number).bitLength() <= 63)) {
                return number.longValue() % divisorLong == 0;
            }
            if (isFloatingType(number)) {
                double value = number.doubleValue();
                long limit = exactFloatingIntegerLimit(number);
                // Within this range an integral floating value has the same
                // integer decimal representation as its lossless long cast.
                // Do not divide in floating point: the quotient may round.
                if (value >= -limit && value <= limit) {
                    return value == Math.rint(value) && (long) value % divisorLong == 0;
                }
            }
        }
        return normalizeDecimal(number).remainder(divisor).signum() == 0;
    }

    private static long exactFloatingIntegerLimit(Number number) {
        return number instanceof Float ? 1L << 24 : 1L << 53;
    }

    /**
     * Compares finite numbers using cross-type decimal numeric semantics.
     * Floating-point values use their Java decimal string representation;
     * no precision already lost during parsing can be recovered.
     */
    public static int compare(Number source, Number target) {
        Asserts.notNull(source, "source");
        Asserts.notNull(target, "target");

        if (isIntegralType(source) && isIntegralType(target)) {
            if (source instanceof BigInteger || target instanceof BigInteger) {
                return toBigInteger(source).compareTo(toBigInteger(target));
            }
            return Long.compare(source.longValue(), target.longValue());
        }

        boolean sourceFloating = isFloatingType(source);
        boolean targetFloating = isFloatingType(target);
        if (sourceFloating || targetFloating) {
            // Same floating type has monotonic decimal representations.
            // Float/Double mixing requires decimal comparison, not widening.
            boolean canCompareAsDouble = sourceFloating && targetFloating
                    && source.getClass() == target.getClass();

            if (!sourceFloating || !targetFloating) {
                Number integer = sourceFloating ? target : source;
                Number floating = sourceFloating ? source : target;
                if (isIntegralType(integer) && !(integer instanceof BigInteger)) {
                    long value = integer.longValue();
                    long limit = exactFloatingIntegerLimit(floating);
                    canCompareAsDouble = value >= -limit && value <= limit;
                }
            }

            if (canCompareAsDouble) {
                double a = source.doubleValue();
                double b = target.doubleValue();
                if (Double.isFinite(a) && Double.isFinite(b)) {
                    return a < b ? -1 : a > b ? 1 : 0; // Don't replace with 'Double.compare()'
                }
            }
        }

        return semanticDecimal(source).compareTo(semanticDecimal(target));
    }


    /**
     * Computes a stable numeric hash across number implementations.
     */
    public static int hash(Number n) {
        if (isIntegralType(n)) {
            if (n instanceof BigInteger) {
                BigInteger value = (BigInteger) n;
                if (value.bitLength() > 63) {
                    return new BigDecimal(value).stripTrailingZeros().hashCode();
                }
            }
            return Long.hashCode(n.longValue());
        }
        if (n instanceof Float || n instanceof Double) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                return Double.hashCode(d);
            }
            // Outside the exact-integer range, the decimal representation
            // can differ from the integer produced by a primitive cast.
            long limit = exactFloatingIntegerLimit(n);
            if (d >= -limit && d <= limit && d == Math.rint(d)) {
                return Long.hashCode((long) d);
            }
        }
        BigDecimal value = normalizeDecimal(n);
        if (value.scale() <= 0 && inLongRange(value)) {
            return Long.hashCode(value.longValue());
        }
        return value.hashCode();
    }

}
