package org.sjf4j.schema;

import org.sjf4j.NodeKind;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.value.ValueInfo;

/**
 * Normalizes a logical value binding before generated structural assertions.
 * Structural Nodes APIs intentionally do not perform implicit value binding.
 */
public final class CompiledSchemaValues {
    private CompiledSchemaValues() {}

    public static Object raw(Object value) {
        if (value == null || NodeKind.plainOf(value.getClass()) != NodeKind.UNKNOWN) {
            return value;
        }
        TypeInfo info = TypeRegistry.registerTypeInfo(value.getClass());
        if (info.valueSet == null) return value;
        ValueInfo binding = info.valueSet.defaultInfo;
        return binding == null ? value : binding.valueToRaw(value);
    }
}
