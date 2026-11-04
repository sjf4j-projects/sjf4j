package org.sjf4j.schema;

/**
 * Runtime support for generated compiled JSON Schema validators.
 * Only operations that cannot be emitted as direct Java access belong here.
 */
public final class CompiledValidatorSupport {
    private CompiledValidatorSupport() {}

    /**
     * Reads a structural object member for generated Schema validators.
     * Unlike a plain Nodes.getInObject call, a POJO member can select a
     * nondefault scalar codec through @NodeProperty(valueFormat/valuePattern).
     * Raw Map/List codec values stay atomic leaves, never traversable nodes.
     */
    public static Object objectPropertyValue(Object owner, String key) {
        Object value = org.sjf4j.Nodes.getInObject(owner, key);
        if (value == null || owner instanceof java.util.Map || owner instanceof org.sjf4j.JsonObject)
            return value;
        org.sjf4j.meta.TypeInfo info =
                org.sjf4j.meta.TypeRegistry.registerTypeInfo(owner.getClass());
        if (info.pojoInfo == null) return value;
        org.sjf4j.meta.PropertyInfo property = info.pojoInfo.getPropertyNoAlias(key);
        if (property == null || property.valueInfo == null) return value;
        org.sjf4j.value.ValueInfo codec = property.valueInfo;
        switch (codec.rawJsonType) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
                return codec.valueToRaw(value);
            default:
                // A codec-backed Map/List is a leaf, not a structural node.
                return value;
        }
    }
}
