package org.sjf4j.util;

import lombok.NonNull;
import org.sjf4j.JsonException;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberUtil {

    private final static int MAX_NUMBER_DIGITS = 100;
    private final static BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private final static BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private final static BigDecimal BD_MIN_LONG = BigDecimal.valueOf(Long.MIN_VALUE);
    private final static BigDecimal BD_MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);

    private static boolean inLongRange(@NonNull BigInteger value) {
        return (value.compareTo(BI_MIN_LONG) >= 0) && (value.compareTo(BI_MAX_LONG) <= 0);
    }

    private static boolean inLongRange(@NonNull BigDecimal value) {
        return (value.compareTo(BD_MIN_LONG) >= 0) && (value.compareTo(BD_MAX_LONG) <= 0);
    }

    private static boolean inLongRange(double value) {
        return (value >= Long.MIN_VALUE) && (value <= Long.MAX_VALUE);
    }


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

    public static Integer asInteger(Number value) {
        Long longValue = asLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + value + "'  not in 32-bit `int` range");
        }
        return longValue.intValue();
    }

    public static Short asShort(Number value) {
        Long longValue = asLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + value + "'  not in 16-bit `short` range");
        }
        return longValue.shortValue();
    }

    public static Byte asByte(Number value) {
        Long longValue = asLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + longValue + "' not in 8-bit `byte` range");
        }
        return longValue.byteValue();
    }

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
            BigDecimal decimal = BigDecimal.valueOf(((Number) value).doubleValue());
            return decimal.toBigInteger();
        }
        return BigInteger.valueOf(((Number) value).longValue());
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


    public static boolean isIntegralType(Number value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
    }

    public static boolean equals(Number source, Number target) {
        if (source == target) return true;
        if (source == null || target == null) return false;
        if (isIntegralType(source) && isIntegralType(target)) {
            return source.longValue() == target.longValue();
        }
        return source.doubleValue() == target.doubleValue();
    }

}
