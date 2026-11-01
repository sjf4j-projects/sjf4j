package org.sjf4j.schema;

import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.value.ValueInfo;

/**
 * Keeps logical value nodes atomic in compiled schema predicates. A raw Map/List
 * from a value binding is a serialization representation, not a navigable
 * object/array node. Structural Nodes methods must never receive that raw value.
 */
public final class CompiledSchemaValues {
    private CompiledSchemaValues() {}

    /**
     * Returns a traversable node, or null for logical value bindings.
     * Does not call valueToRaw: the binding must not be exposed as a tree.
     */
    public static Object structuralNode(Object value) {
        if (value == null || NodeKind.plainOf(value.getClass()) != NodeKind.UNKNOWN) {
            return value;
        }
        TypeInfo info = TypeRegistry.registerTypeInfo(value.getClass());
        return info.valueSet == null ? value : null;
    }

    /**
     * JSON type for compiled assertions, preserving the leaf identity of a
     * container-valued binding (matching Instance.infer in SchemaPlan).
     */
    public static JsonType schemaType(Object value) {
        if (value == null) return JsonType.NULL;
        Class<?> type = value.getClass();
        if (NodeKind.plainOf(type) != NodeKind.UNKNOWN) return JsonType.rawOf(type);
        TypeInfo info = TypeRegistry.registerTypeInfo(type);
        if (info.valueSet != null) {
            ValueInfo binding = info.valueSet.defaultInfo;
            if (binding == null) return JsonType.UNKNOWN;
            JsonType raw = binding.rawJsonType;
            return raw == JsonType.OBJECT || raw == JsonType.ARRAY
                    ? JsonType.UNKNOWN : raw;
        }
        return JsonType.rawOf(type);
    }
}
