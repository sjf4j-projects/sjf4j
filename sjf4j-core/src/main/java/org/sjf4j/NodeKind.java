        if (kind != NodeKind.UNKNOWN) {
            // Some external tree types also implement Map/List (JSON-P).
            // Do not let those Java interfaces mask the registered representation.
            if ((kind == OBJECT_MAP
                    && clazz != java.util.HashMap.class
                    && clazz != java.util.LinkedHashMap.class
                    && clazz != java.util.TreeMap.class)
                    || (kind == ARRAY_LIST
                    && clazz != java.util.ArrayList.class
                    && clazz != java.util.LinkedList.class)) {
                TypeInfo info = TypeRegistry.registerTypeInfo(clazz);
                if (info.externalNode != null) return info.externalNode.nodeKind(node);
            }
            return kind;
        }

        TypeInfo ti = TypeRegistry.registerTypeInfo(clazz);
        if (ti.valueInfos != null) {
            return NodeKind.VALUE_BINDING;
        } else if (ti.oneOfInfo != null) {
            return NodeKind.UNKNOWN;
        } else if (ti.externalNode != null) {
            return ti.externalNode.nodeKind(node);
        } else if (ti.pojoInfo != null) {
            return NodeKind.OBJECT_POJO;
        }

        return NodeKind.UNKNOWN;
    }

    public static NodeKind plainOf(Class<?> clazz) {
        Asserts.notNull(clazz, "clazz");
        if (clazz == Object.class) {
            return UNKNOWN;
        } else if (clazz.isPrimitive()) {
            if (clazz == char.class) {
                return VALUE_STRING;
            } else if (clazz == boolean.class) {
                return VALUE_BOOLEAN;
            } else if (clazz == void.class) {
                return VALUE_NULL;
            } else {
                return VALUE_NUMBER;
            }
        } else if (clazz == String.class) {
            return VALUE_STRING;
        } else if (Number.class.isAssignableFrom(clazz)) {
            return VALUE_NUMBER;
        } else if (clazz == Boolean.class) {
            return VALUE_BOOLEAN;
        } else if (Map.class.isAssignableFrom(clazz)) {
            return OBJECT_MAP;
        } else if (clazz == JsonObject.class) {
            return OBJECT_JSON_OBJECT;
        } else if (JsonObject.class.isAssignableFrom(clazz)) {
            return OBJECT_JOJO;
        } else if (List.clas        if (kind != NodeKind.UNKNOWN) {
            if (kind == OBJECT_MAP || kind == ARRAY_LIST) {
                org.sjf4j.external.ExternalNode<Object> external =
                        TypeRegistry.externalForContainer(clazz);
                if (external != null) return external.nodeKind(node);
            }
            return kind;
        }

this == VALUE_STRING || this == VALUE_STRING_CHARACTER
                || this == VALUE_STRING_ENUM || this == VALUE_STRING_EXTERNAL;
    }

    public boolean isBoolean() {
        return this == VALUE_BOOLEAN || this == VALUE_BOOLEAN_EXTERNAL;
    }

    public boolean isNull() {
        return this == VALUE_NULL;
    }

    public boolean isValue() {
        return isNumber() || isString() || isBoolean() || isNull()
                || this == VALUE_BINDING;
    }

    public boolean isObject() {
        return this == OBJECT_MAP || this == OBJECT_JSON_OBJECT
                || this == OBJECT_JOJO || this == OBJECT_POJO
                || this == OBJECT_EXTERNAL;
    }

    public boolean isArray() {
        return this == ARRAY_LIST || this == ARRAY_JSON_ARRAY
                || this == ARRAY_JAJO || this == ARRAY_ARRAY
                || this == ARRAY_SET || this == ARRAY_EXTERNAL;
    }

    public boolean isContainer() {
        return isObject() || isArray();
    }

    /**
     * Returns true for unknown/unclassified kind.
     */
    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * Returns true for raw codec representations: scalar values, {@link Map}, or
     * {@link List}.
     */
    public boolean isRaw() {
        return this == VALUE_STRING || this == VALUE_NUMBER || this == VALUE_BOOLEAN || this == VALUE_NULL ||
                this == OBJECT_MAP || this == ARRAY_LIST;
    }

}
