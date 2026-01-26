package org.sjf4j.node;

import org.sjf4j.JsonException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Utility class for handling number operations, conversions, and type checking.
 * 
 * <p>This class provides a set of static methods for safe numeric conversions between
 * different Java number types, with range checking and appropriate exception handling.
 * It also includes utility methods for checking numeric types and string representations.
 */
public class Numbers {

    /**
     * Maximum number of digits allowed for numeric numbers.
     */
    private final static int MAX_NUMBER_DIGITS = 100;
    
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
     *
     * @param number the BigInteger number to check
     * @return true if the number is within Long range, false otherwise
     * @throws IllegalArgumentException if number is null
     */
    private static boolean inLongRange(BigInteger number) {
        Objects.requireNonNull(number, "number is null");
        return (number.compareTo(BI_MIN_LONG) >= 0) && (number.compareTo(BI_MAX_LONG) <= 0);
    }

    /**
     * Checks if a BigDecimal number is within the range of a Long.
     *
     * @param number the BigDecimal number to check
     * @return true if the number is within Long range, false otherwise
     * @throws IllegalArgumentException if number is null
     */
    private static boolean inLongRange(BigDecimal number) {
        Objects.requireNonNull(number, "number is null");
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
     *
     * @param number the Number to convert
     * @return the Long representation
     * @throws IllegalArgumentException if the number is outside the Long range
     */
    public static Long asLong(Number number) {
        if (number == null) return null;
        if (number instanceof Long) return (Long) number;
        if ((number instanceof Double || number instanceof Float) && !inLongRange(number.doubleValue())) {
            throw new JsonException("Cannot convert floating-point Number '" + number + "' to Long: out of 64-bit range");
        }
        if (number instanceof BigInteger && !inLongRange((BigInteger) number)) {
            throw new JsonException("Cannot convert BigInteger '" + number + "' to Long: out of 64-bit range");
        }
        if (number instanceof BigDecimal && !inLongRange((BigDecimal) number)) {
            throw new JsonException("Cannot convert BigDecimal '" + number + "' to Long: out of 64-bit range");
        }
        return number.longValue();
    }

    /**
     * Converts a Number to an Integer with range checking.
     *
     * @param number the Number to convert
     * @return the Integer representation
     * @throws IllegalArgumentException if the number is outside the Integer range
     */
    public static Integer asInteger(Number number) {
        Long longValue = asLong(number);
        if (longValue == null) return null;
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new JsonException("Cannot convert Number '" + number + "' to Integer: out of 32-bit range");
        }
        return longValue.intValue();
    }

    public static Short asShort(Number number) {
        Long longValue = asLong(number);
        if (longValue == null) return null;
        if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
            throw new JsonException("Cannot convert Number '" + number + "' to Short: out of 16-bit range");
        }
        return longValue.shortValue();
    }

    public static Byte asByte(Number number) {
        Long longValue = asLong(number);
        if (longValue == null) return null;
        if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
            throw new JsonException("Cannot convert Number '" + number + "' to Byte: out of 8-bit range");
        }
        return longValue.byteValue();
    }

    /**
     * Converts a Number to a Double with range checking.
     *
     * @param number the Number to convert
     * @return the Double representation
     * @throws IllegalArgumentException if the number is not a finite Double
     */
    public static Double asDouble(Number number) {
        if (number == null) return null;
        if (number instanceof Double) return (Double) number;

        double d = number.doubleValue();
        if (!Double.isFinite(d)) {
            throw new JsonException("Cannot convert Number '" + number + "' to Double: non-finite value");
        }
        return d;
    }

    /**
     * Converts a Number to a Float with range checking.
     *
     * @param number the Number to convert
     * @return the Float representation
     * @throws IllegalArgumentException if the number is not a finite Float
     */
    public static Float asFloat(Number number) {
        if (number == null) return null;
        if (number instanceof Float) return (Float) number;

        float f = number.floatValue();
        if (!Float.isFinite(f)) {
            throw new JsonException("Cannot convert Number '" + number + "' to Float: non-finite value");
        }
        return f;
    }

    public static BigInteger asBigInteger(Number number) {
        if (number == null) return null;
        if (number instanceof BigInteger) return (BigInteger) number;
        if (number instanceof BigDecimal) return ((BigDecimal) number).toBigInteger();
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (!Double.isFinite(d)) {
                throw new JsonException("Cannot convert non-finite floating-point '" + number + "' to BigInteger");
            }
            return BigInteger.valueOf((long) d);
        }
        return BigInteger.valueOf(number.longValue());
    }

    public static BigDecimal asBigDecimal(Number number) {
        if (number == null) return null;
        if (number instanceof BigDecimal) return (BigDecimal) number;
        if (number instanceof BigInteger) return new BigDecimal((BigInteger) number);
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(number.longValue());
    }

    @SuppressWarnings("unchecked")
    public static <T> T as(Number number, Class<T> clazz) {
        if (clazz == null || clazz.isAssignableFrom(number.getClass())) return (T) number;
        if (clazz == long.class || clazz == Long.class) return (T) Numbers.asLong(number);
        if (clazz == int.class || clazz == Integer.class) return (T) Numbers.asInteger( number);
        if (clazz == short.class || clazz == Short.class) return (T) Numbers.asShort(number);
        if (clazz == byte.class || clazz == Byte.class) return (T) Numbers.asByte(number);
        if (clazz == double.class || clazz == Double.class) return (T) Numbers.asDouble(number);
        if (clazz == float.class || clazz == Float.class) return (T) Numbers.asFloat(number);
        if (clazz == BigInteger.class) return (T) Numbers.asBigInteger(number);
        if (clazz == BigDecimal.class) return (T) Numbers.asBigDecimal(number);
        throw new JsonException("Cannot convert " + Types.name(number) + " '" + number + "' to " + clazz.getName());
    }


    public static Number toNumber(String text) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("text is null or empty");

        if (text.length() > MAX_NUMBER_DIGITS) {
            throw new IllegalArgumentException("Number too large (" + text.length() + " digits): '" +
                    text.substring(0, 20) + "'");
        }
        if (text.contains(".") || text.contains("e") || text.contains("E")) {
            try {
                // `parseDouble` is faster than `parseFloat`
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return new BigDecimal(text);
            }
        } else {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ex) {
                    return new BigInteger(text);
                }
            }
        }
    }


    /**
     * Checks if a given string represents a numeric number.
     * <p>
     * This method supports:
     * - Integers (e.g., "42", "-7", "+8")
     * - Floating-point numbers (e.g., "3.14", "-0.5", ".5")
     * - Scientific notation (e.g., "1e3", "-2.5E-4")
     * - Special floating-point numbers: ".nan", ".inf", "-.inf"
     * - Underscore separators in numbers (YAML style, e.g., "1_000_000")
     * <p>
     * Rules:
     * - Leading '+' or '-' is allowed at the start or immediately after 'e'/'E'
     * - Only one decimal point is allowed, and it must appear before any 'e'/'E'
     * - Only one 'e'/'E' is allowed for scientific notation
     *
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

    public static boolean isSemanticInteger(Number number) {
        if (number == null) return false;
        if (isIntegralType(number)) return true;
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).stripTrailingZeros().scale() <= 0;
        }
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            return d % 1 == 0 && Double.isFinite(d);
        }
        return false;
    }

    public static BigDecimal normalizeDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).stripTrailingZeros();
        }
        return new BigDecimal(number.toString()).stripTrailingZeros();
    }

    public static int compare(Number source, Number target) {
        Objects.requireNonNull(source, "source is null");
        Objects.requireNonNull(target, "target is null");
        if (source instanceof BigInteger || target instanceof BigInteger) {
            return asBigInteger(source).compareTo(asBigInteger(target));
        }
        if (isIntegralType(source) && isIntegralType(target)) {
            return Long.compare(source.longValue(), target.longValue());
        }
        return asBigDecimal(source).compareTo(asBigDecimal(target));
    }

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
