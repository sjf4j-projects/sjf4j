package org.sjf4j.node;

import org.sjf4j.exception.JsonException;
import org.sjf4j.util.Strings;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Numeric conversion helpers with range checks.
 */
public final class Numbers {

    /**
     * Maximum number of digits allowed for numeric numbers.
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
        Objects.requireNonNull(number, "number");
        return (number.compareTo(BI_MIN_LONG) >= 0) && (number.compareTo(BI_MAX_LONG) <= 0);
    }

    /**
     * Checks if a BigDecimal number is within the range of a Long.
     */
    private static boolean inLongRange(BigDecimal number) {
        Objects.requireNonNull(number, "number");
        return (number.compareTo(BD_MIN_LONG) >= 0) && (number.compareTo(BD_MAX_LONG) <= 0);
    }

    /**
     * Checks if a double number is within the range of a Long.
     *
     * @param number the double number to check
     * @return true if the number is within Long range, false otherwise
     */
    private static boolean inLongRange(double number) {
        return (number >= Long.MIN_VALUE) && (number <= Long.MAX_VALUE);
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
        Objects.requireNonNull(number, "number");
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
        Objects.requireNonNull(number, "number");
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
        Objects.requireNonNull(number, "number");
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
        Objects.requireNonNull(number, "number");
        if (number instanceof BigInteger) return (BigInteger) number;
        if (number instanceof BigDecimal) return ((BigDecimal) number).toBigInteger();
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                throw new ArithmeticException("cannot convert non-finite floating-point '" + number + "' to BigInteger");
            }
            return BigInteger.valueOf((long) d);
        }
        return BigInteger.valueOf(number.longValue());
    }

    /**
     * Converts a Number to BigDecimal.
     */
    public static BigDecimal toBigDecimal(Number number) {
        Objects.requireNonNull(number, "number");
        if (number instanceof BigDecimal) return (BigDecimal) number;
        if (number instanceof BigInteger) return new BigDecimal((BigInteger) number);
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(number.longValue());
    }

    /**
     * Converts a Number to the requested numeric target type.
     */
    @SuppressWarnings("unchecked")
    public static <T> T to(Number number, Class<T> clazz) {
        Objects.requireNonNull(number, "number");
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
        throw new JsonException("cannot convert " + Types.name(number) + " '" + number + "' to " + clazz.getName());
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
        if (text == null || text.isEmpty()) {
            throw new NumberFormatException("invalid number text: value is null or empty");
        }

        final int len = text.length();
        if (len > MAX_NUMBER_LENGTH) {
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

            // Floating-point number: delegate syntax validation to the JDK parser.
            if (c == '.' || c == 'e' || c == 'E') {
                return _parseFloatingNumber(text);
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
     * Parses a simple decimal literal in-place from a character sequence.
     *
     * <p>This is a low-allocation helper for parser hot paths that already own
     * the backing sequence and cursor. It is intentionally not a full
     * {@link Double#parseDouble(String)} replacement: it accepts only the simple
     * decimal forms currently used by JSONPath filter literals. In particular,
     * it does not consume exponent notation, leading plus signs, NaN, or
     * Infinity.</p>
     */
    public static double parseDoubleLiteral(CharSequence text, int[] pos) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(pos, "pos");

        int start = pos[0];
        boolean negative = false;
        if (pos[0] < text.length() && text.charAt(pos[0]) == '-') {
            negative = true;
            pos[0]++;
        }

        double value = 0;
        boolean hasDigit = false;
        while (pos[0] < text.length()) {
            char c = text.charAt(pos[0]);
            if (!Character.isDigit(c)) break;
            hasDigit = true;
            value = value * 10 + (c - '0');
            pos[0]++;
        }

        if (pos[0] < text.length() && text.charAt(pos[0]) == '.') {
            pos[0]++;
            double scale = 0.1d;
            while (pos[0] < text.length()) {
                char c = text.charAt(pos[0]);
                if (!Character.isDigit(c)) break;
                hasDigit = true;
                value += (c - '0') * scale;
                scale *= 0.1d;
                pos[0]++;
            }
        }

        if (!hasDigit) {
            throw new NumberFormatException("Invalid number literal at pos " + start);
        }

        return negative ? -value : value;
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
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).stripTrailingZeros();
        }
        return new BigDecimal(number.toString()).stripTrailingZeros();
    }

    /**
     * Compares two numbers with cross-type numeric semantics.
     */
    public static int compare(Number source, Number target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source instanceof BigInteger || target instanceof BigInteger) {
            return toBigInteger(source).compareTo(toBigInteger(target));
        }
        if (isIntegralType(source) && isIntegralType(target)) {
            return Long.compare(source.longValue(), target.longValue());
        }
        return toBigDecimal(source).compareTo(toBigDecimal(target));
    }

    /**
     * Computes a stable numeric hash across number implementations.
     */
    public static int hash(Number n) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            long v = n.longValue();
            return Long.hashCode(v);
        }

        if (n instanceof Float || n instanceof Double) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return Double.hashCode(d);
            }
            long lv = (long) d;
            if (d == lv) return Long.hashCode(lv);
        }

        if (n instanceof BigInteger) {
            return n.hashCode();
        }

        if (n instanceof BigDecimal) {
            BigDecimal bd = ((BigDecimal) n).stripTrailingZeros();
            return bd.hashCode();
        }

        BigDecimal bd = new BigDecimal(n.toString()).stripTrailingZeros();
        return bd.hashCode();
    }

}
