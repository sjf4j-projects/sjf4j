package org.sjf4j;

import lombok.NonNull;

import java.math.BigDecimal;

public enum ValueType {
    NULL,
    STRING,
    NUMBER_INT,
    NUMBER_FLOAT,
    BOOLEAN,
    OBJECT,
    ARRAY,
    UNKNOWN;

    public static ValueType of(Object value) {
        if (value == null) {
            return NULL;
        } else if (value instanceof String || value instanceof Character) {
            return STRING;
        } else if (value instanceof Number) {
            if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
                return NUMBER_FLOAT;
            } else {
                return NUMBER_INT;
            }
        } else if (value instanceof Boolean) {
            return BOOLEAN;
        } else if (value instanceof JsonObject) {
            return OBJECT;
        } else if (value instanceof JsonArray) {
            return ARRAY;
        }
        return UNKNOWN;
    }

    public static ValueType of(@NonNull Class<?> clazz) {
        if (clazz == String.class) {
            return STRING;
        } else if (Number.class.isAssignableFrom(clazz)) {
            if (clazz == Double.class || clazz == Float.class || clazz == BigDecimal.class) {
                return NUMBER_FLOAT;
            } else {
                return NUMBER_INT;
            }
        } else if (clazz == Boolean.class) {
            return BOOLEAN;
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return OBJECT;
        } else if (JsonArray.class.isAssignableFrom(clazz)) {
            return ARRAY;
        } else if (clazz == Void.class) {
            return NULL;
        }
        return UNKNOWN;
    }

    public boolean isValue() {
        return this == STRING || this == NUMBER_INT || this == NUMBER_FLOAT || this == BOOLEAN || this == NULL;
    }

    public boolean isNumber() {
        return this == NUMBER_INT || this == NUMBER_FLOAT;
    }

    public boolean isContainer() {
        return this == OBJECT || this == ARRAY;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

}
