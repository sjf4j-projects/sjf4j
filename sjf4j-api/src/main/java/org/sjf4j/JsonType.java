package org.sjf4j;

import java.math.BigDecimal;

public enum JsonType {
    NULL,
    STRING,
    NUMBER_INT,
    NUMBER_FLOAT,
    BOOLEAN,
    OBJECT,
    ARRAY,
    UNKNOWN;

    public static JsonType of(Object value) {
        if (value == null) {
            return NULL;
        } else if (value instanceof String) {
            return STRING;
        } else if (value instanceof Boolean) {
            return BOOLEAN;
        } else if (value instanceof Number) {
            if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
                return NUMBER_FLOAT;
            }
            return NUMBER_INT;
        } else if (value instanceof JsonObject) {
            return OBJECT;
        } else if (value instanceof JsonArray) {
            return ARRAY;
        }
        return UNKNOWN;
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
