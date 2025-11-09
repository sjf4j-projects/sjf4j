package org.sjf4j;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public enum NodeType {
    ANY,
    VALUE_VOID,
    VALUE_STRING,
    VALUE_NUMBER,
    VALUE_BOOLEAN,
    OBJECT_JO,
    OBJECT_MAP,
    OBJECT_POJO,
    ARRAY_JA,
    ARRAY_LIST,
    ARRAY_ARRAY,
    UNKNOWN;

    public static NodeType of(Type type) {
        Class<?> clazz;
        if (type == null) {
            return ANY;
        } else if (type instanceof Class<?>) {
            clazz = (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType == Map.class) {
                return OBJECT_MAP;
            } else if (rawType == List.class) {
                return ARRAY_LIST;
            } else {
                return UNKNOWN;
            }
        } else if (type instanceof GenericArrayType) {
            return ARRAY_ARRAY;
        } else {
            return UNKNOWN;
        }

        if (clazz == Object.class) {
            return ANY;
        } else if (clazz.isPrimitive()) {
            if (clazz == char.class) {
                return VALUE_STRING;
            } else if (clazz == boolean.class) {
                return VALUE_BOOLEAN;
            } else if (clazz == void.class) {
                return VALUE_VOID;
            } else {
                return VALUE_NUMBER;
            }
        } else if (CharSequence.class.isAssignableFrom(clazz) || clazz == Character.class) {
            return VALUE_STRING;
        } else if (Number.class.isAssignableFrom(clazz)) {
            return VALUE_NUMBER;
        } else if (clazz == Boolean.class) {
            return VALUE_BOOLEAN;
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return OBJECT_JO;
        } else if (clazz == Map.class) {
            return OBJECT_MAP;
        } else if (JsonArray.class.isAssignableFrom(clazz)) {
            return ARRAY_JA;
        } else if (clazz == List.class) {
            return ARRAY_LIST;
        } else if (clazz.isArray()) {
            return ARRAY_ARRAY;
        } else if (clazz == Void.class) {
            return VALUE_VOID;
        } else if (PojoRegistry.isPojo(clazz)) {
            return OBJECT_POJO;
        }
        return UNKNOWN;
    }

    public boolean isAny() {
        return this == ANY;
    }

    public boolean isNumber() {
        return this == VALUE_NUMBER;
    }
    public boolean isNumberOrAny() {
        return isNumber() || isAny();
    }

    public boolean isValue() {
        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN || this == VALUE_VOID;
    }
    public boolean isValueOrAny() {
        return isValue() || isAny();
    }

    public boolean isObject() {
        return this == OBJECT_JO || this == OBJECT_MAP || this == OBJECT_POJO;
    }
    public boolean isObjectOrAny() {
        return isObject() || isAny();
    }

    public boolean isArray() {
        return this == ARRAY_JA || this == ARRAY_LIST || this == ARRAY_ARRAY;
    }
    public boolean isArrayOrAny() {
        return isArray() || isAny();
    }

    public boolean isContainer() {
        return isObject() || isArray();
    }
    public boolean isContainerOrAny() {
        return isContainer() || isAny();
    }

    public boolean isPured() {
        return isValue() || this == OBJECT_JO || this == ARRAY_JA;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

}
