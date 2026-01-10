package org.sjf4j.util;

import org.sjf4j.JsonException;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Utility class for handling number operations, conversions, and type checking.
 * 
 * <p>This class provides a set of static methods for safe numeric conversions between
 * different Java number types, with range checking and appropriate exception handling.
 * It also includes utility methods for checking numeric types and string representations.
 */
public class NumberUtil {

    /**
     * Maximum number of digits allowed for numeric values.
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
     * Checks if a BigInteger value is within the range of a Long.
     *
     * @param value the BigInteger value to check
     * @return true if the value is within Long range, false otherwise
     * @throws IllegalArgumentException if value is null
     */
    private static boolean inLongRange(BigInteger value) {
        if (value == null) throw new IllegalArgumentException("Value must not be null");
        return (value.compareTo(BI_MIN_LONG) >= 0) && (value.compareTo(BI_MAX_LONG) <= 0);
    }

    /**
     * Checks if a BigDecimal value is within the range of a Long.
     *
     * @param value the BigDecimal value to check
     * @return true if the value is within Long range, false otherwise
     * @throws IllegalArgumentException if value is null
     */
    private static boolean inLongRange(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Value must not be null");
        return (value.compareTo(BD_MIN_LONG) >= 0) && (value.compareTo(BD_MAX_LONG) <= 0);
    }

    /**
     * Checks if a double value is within the range of a Long.
     *
     * @param value the double value to check
     * @return true if the value is within Long range, false otherwise
     */
    private static boolean inLongRange(double value) {
        return (value >= Long.MIN_VALUE) && (value <= Long.MAX_VALUE);
    }


    /**
     * Checks if a Number is an integral type (Byte, Short, Integer, or Long).
     *
     * @param value the Number to check
     * @return true if the Number is an integral type, false otherwise
     */
    public static boolean isIntegralType(Number value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
    }

    /**
     * Checks if a Number is a floating point type (Float or Double).
     *
     * @param value the Number to check
     * @return true if the Number is a floating point type, false otherwise
     */
    private static boolean isFloatingType(Number value) {
        return value instanceof Float || value instanceof Double;
    }

    /**
     * Converts a Number to a Long with range checking.
     *
     * @param value the Number to convert
     * @return the Long representation
     * @throws IllegalArgumentException if the value is outside the Long range
     */
    public static Long asLong(Number value) {
        if (value == null) {
            return null;
        } else if (value instanceof Long) {
            return (Long) value;
        } else if (((value instanceof Double || value instanceof Float) && !inLongRange((double) value)) ||
                (value instanceof BigInteger && !inLongRange((BigInteger) value)) ||
                (value instanceof BigDecimal && !inLongRange((BigDecimal) value))) {
            throw new IllegalArgumentException("Numeric value '" + value + "' not in 64-bit `long` range");
        }
        return ((Number) value).longValue();
    }

    /**
     * Converts a Number to an Integer with range checking.
     *
     * @param value the Number to convert
     * @return the Integer representation
     * @throws IllegalArgumentException if the value is outside the Integer range
     */
    public static Integer asInteger(Number value) {
        Long longValue = asLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + value + "'  not in 32-bit `int` range");
        }
        return longValue.intValue();
    }

    /**
     * Converts a Number to a Short with range checking.
     *
     * @param value the Number to convert
     * @return the Short representation
     * @throws IllegalArgumentException if the value is outside the Short range
     */
    public static Short asShort(Number value) {
        Long longValue = asLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + value + "'  not in 16-bit `short` range");
        }
        return longValue.shortValue();
    }

    /**
     * Converts a Number to a Byte with range checking.
     *
     * @param value the Number to convert
     * @return the Byte representation
     * @throws IllegalArgumentException if the value is outside the Byte range
     */
    public static Byte asByte(Number value) {
        Long longValue = asLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + longValue + "' not in 8-bit `byte` range");
        }
        return longValue.byteValue();
    }

    /**
     * Converts a Number to a Double with range checking.
     *
     * @param value the Number to convert
     * @return the Double representation
     * @throws IllegalArgumentException if the value is not a finite Double
     */
    public static Double asDouble(Number value) {
        if (value == null) {
            return null;
        } else if (value instanceof Double) {
            return (Double) value;
        }
        double dValue = ((Number) value).doubleValue();
        if (!Double.isFinite(dValue)) {
            throw new IllegalArgumentException("Numeric value '" + value + "' not in 64-bit `double` range");
        }
        return dValue;
    }

    /**
     * Converts a Number to a Float with range checking.
     *
     * @param value the Number to convert
     * @return the Float representation
     * @throws IllegalArgumentException if the value is not a finite Float
     */
    public static Float asFloat(Number value) {
        if (value == null) {
            return null;
        } else if (value instanceof Float) {
            return (Float) value;
        }
        float fValue = ((Number) value).floatValue();
        if (!Float.isFinite(fValue)) {
            throw new IllegalArgumentException("Numeric value '" + value + "' not in 32-bit `float` range");
        }
        return fValue;
    }

    public static BigInteger asBigInteger(Number value) {
        if (value == null) {
            return null;
        } else if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toBigInteger();
        } else if (value instanceof Double || value instanceof Float) {
            BigDecimal decimal = BigDecimal.valueOf(value.doubleValue());
            return decimal.toBigInteger();
        }
        return BigInteger.valueOf(value.longValue());
    }

    public static BigDecimal asBigDecimal(Number value) {
        if (value == null) {
            return null;
        } else if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        } else if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.valueOf(((Number) value).longValue());
    }

    @SuppressWarnings("unchecked")
    public static <T> T as(Number value, Class<T> clazz) {
        if (clazz == null || clazz.isAssignableFrom(value.getClass())) {
            return (T) value;
        } else if (clazz == long.class || clazz == Long.class) {
            return (T) NumberUtil.asLong(value);
        } else if (clazz == int.class || clazz == Integer.class) {
            return (T) NumberUtil.asInteger( value);
        } else if (clazz == short.class || clazz == Short.class) {
            return (T) NumberUtil.asShort(value);
        } else if (clazz == byte.class || clazz == Byte.class) {
            return (T) NumberUtil.asByte(value);
        } else if (clazz == double.class || clazz == Double.class) {
            return (T) NumberUtil.asDouble(value);
        } else if (clazz == float.class || clazz == Float.class) {
            return (T) NumberUtil.asFloat(value);
        } else if (clazz == BigInteger.class) {
            return (T) NumberUtil.asBigInteger(value);
        } else if (clazz == BigDecimal.class) {
            return (T) NumberUtil.asBigDecimal(value);
        } else {
            throw new JsonException("Cannot convert numeric value '" + value + "' to type " + clazz);
        }
    }


    public static Number toNumber(String num) {
        if (num == null || num.isEmpty()) {
            throw new IllegalArgumentException("Input number string is null or empty");
        }
        if (num.length() > MAX_NUMBER_DIGITS) {throw new IllegalArgumentException("Number too large ("
                + num.length() + " digits): '" + num.substring(0, 20) + "'");
        }
        if (num.contains(".") || num.contains("e") || num.contains("E")) {
            try {
                // `parseDouble` is faster than `parseFloat`
                return Double.parseDouble(num);
            } catch (NumberFormatException e) {
                return new BigDecimal(num);
            }
        } else {
            try {
                return Integer.parseInt(num);
            } catch (NumberFormatException e) {
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException ex) {
                    return new BigInteger(num);
                }
            }
        }
    }


    /**
     * Checks if a given string represents a numeric value.
     * <p>
     * This method supports:
     * - Integers (e.g., "42", "-7", "+8")
     * - Floating-point numbers (e.g., "3.14", "-0.5", ".5")
     * - Scientific notation (e.g., "1e3", "-2.5E-4")
     * - Special floating-point values: ".nan", ".inf", "-.inf"
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

    public static boolean isInteger(Number value) {
        if (value instanceof Integer || value instanceof Long ||
                value instanceof Short || value instanceof Byte) {
            return true;
        }

        if (value instanceof BigInteger) {
            return true;
        }

        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).scale() <= 0;
        }

        if (value instanceof Double || value instanceof Float) {
            double d = value.doubleValue();
            return d % 1 == 0;
        }

        return false;
    }

    public static BigDecimal normalize(Number value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).stripTrailingZeros();
        }
        return new BigDecimal(value.toString()).stripTrailingZeros();
    }

    public static int compare(Number source, Number target) {
        if (source == null || target == null) throw new IllegalArgumentException("Source and target must not be null");
        if (source instanceof BigDecimal || target instanceof BigDecimal) {
            return asBigDecimal(source).compareTo(asBigDecimal(target));
        }
        if (source instanceof BigInteger || target instanceof BigInteger) {
            return asBigInteger(source).compareTo(asBigInteger(target));
        }
        if (isIntegralType(source) && isIntegralType(target)) {
            return Long.compare(source.longValue(), target.longValue());
        }
        return Double.compare(source.doubleValue(), target.doubleValue());
    }

    public static int hashCode(Number n) {
        if (n instanceof Integer || n instanceof Long
                || n instanceof Short || n instanceof Byte) {
            long v = n.longValue();
            return Long.hashCode(v);
        }

        if (n instanceof Float || n instanceof Double) {
            double d = n.doubleValue();

            // NaN / Infinity 直接走 equals 语义
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return Double.hashCode(d);
            }

            // 尝试整数快路径
            long lv = (long) d;
            if (d == lv) {
                return Long.hashCode(lv);
            }
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
