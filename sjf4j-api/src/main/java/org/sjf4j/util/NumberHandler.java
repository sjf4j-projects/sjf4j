package org.sjf4j.util;

import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberHandler {

    public final static int MAX_NUMBER_DIGITS = 100;
    public final static BigInteger BI_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    public final static BigInteger BI_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    public final static BigDecimal BD_MIN_LONG = BigDecimal.valueOf(Long.MIN_VALUE);
    public final static BigDecimal BD_MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);

    public static boolean inLongRange(@NonNull BigInteger value) {
        return (value.compareTo(BI_MIN_LONG) >= 0) && (value.compareTo(BI_MAX_LONG) <= 0);
    }

    public static boolean inLongRange(@NonNull BigDecimal value) {
        return (value.compareTo(BD_MIN_LONG) >= 0) && (value.compareTo(BD_MAX_LONG) <= 0);
    }

    public static boolean inLongRange(double value) {
        return (value >= Long.MIN_VALUE) && (value <= Long.MAX_VALUE);
    }


    public static Long numberAsLong(Number value) {
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

    public static Integer numberAsInteger(Number value) {
        Long longValue = numberAsLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + value + "'  not in 32-bit `int` range");
        }
        return longValue.intValue();
    }

    public static Short numberAsShort(Number value) {
        Long longValue = numberAsLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + value + "'  not in 16-bit `short` range");
        }
        return longValue.shortValue();
    }

    public static Byte numberAsByte(Number value) {
        Long longValue = numberAsLong(value);
        if (longValue == null) {
            return null;
        } else if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Numeric value '" + longValue + "' not in 8-bit `byte` range");
        }
        return longValue.byteValue();
    }

    public static Double numberAsDouble(Number value) {
        if (value == null) {
            return null;
        } else if (value instanceof Double) {
            return (Double) value;
        }
        double dValue = ((Number) value).doubleValue();
        if (!Double.isFinite(dValue)) {
            throw new IllegalArgumentException("Numeric value '" + ((Number) value) + "' not in 64-bit `double` range");
        }
        return dValue;
    }

    public static Float numberAsFloat(Number value) {
        if (value == null) {
            return null;
        } else if (value instanceof Float) {
            return (Float) value;
        }
        float fValue = ((Number) value).floatValue();
        if (!Float.isFinite(fValue)) {
            throw new IllegalArgumentException("Numeric value '" + ((Number) value) + "' not in 32-bit `float` range");
        }
        return fValue;
    }

    public static BigInteger numberAsBigInteger(Number value) {
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

    public static BigDecimal numberAsBigDecimal(Number value) {
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


    public static Number stringToNumber(String num) {
        if (num == null || num.isEmpty()) {
            throw new IllegalArgumentException("Number is empty");
        }
        if (num.length() > MAX_NUMBER_DIGITS) {
            throw new IllegalArgumentException("Number too large: " + num.length() + " digits");
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

}
