package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.NodeKind;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.path.PathSegment;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.util.Asserts;
import org.sjf4j.value.ValueInfo;

import java.io.Reader;
import java.util.Map;

/**
 * Shared schema JSON binding and runtime support for generated validators.
 * Keeps schema document parsing independent of application runtime settings.
 */
public final class SchemaSupport {
    private SchemaSupport() {}

    /** Only schema JSON binding needs to initialize a backend. */
    private static final class BinderHolder {
        private static final Binder<?, ?> BINDER =
                BinderFactory.jsonProvider().create(NodeConfig.EXACT_NUMBERS);
    }

    static JsonSchema readSchema(String json) {
        return (JsonSchema) BinderHolder.BINDER.readNode(json, JsonSchema.class);
    }

    static JsonSchema readSchema(Reader reader) {
        return (JsonSchema) BinderHolder.BINDER.readNode(reader, JsonSchema.class);
    }

    /** Reads schema JSON literals and documents with exact generic numbers. */
    public static Object readJsonValue(String json) {
        return BinderHolder.BINDER.readNode(json, Object.class);
    }

    /** Writes a schema document or literal for compiled schema snapshots. */
    public static String writeJsonValue(Object node) {
        return BinderHolder.BINDER.writeNodeAsString(node);
    }

    /**
     * Executes a compiled validator's diagnostic fallback at its original
     * instance location, without exposing path-aware SchemaPlan APIs.
     */
    public static void requireValid(SchemaPlan plan, Object node,
                                    PathSegment instancePs, boolean strictFormat) {
        ValidationContext ctx = new ValidationContext(true, strictFormat, false);
        if (!plan.evaluate(node, instancePs, ctx)) {
            throw new ValidationException(ctx.toResult(false));
        }
    }

    /**
     * Reads a member for generated schema validation using its scalar raw
     * ValueCodec representation. Property-level formats take precedence over
     * the runtime config's format selection.
     *
     * <p>Map/List-encoded value nodes remain atomic: their raw containers must
     * not be traversed as structural schema objects or arrays.
     */
    public static Object scalarValueInObject(Object owner, String key, NodeConfig config) {
        Asserts.notNull(config, "config");
        Object value = NodeWalker.getInObject(owner, key);
        if (value == null) return null;

        ValueInfo codec = null;
        if (!(owner instanceof Map) && owner.getClass() != JsonObject.class) {
            TypeInfo ownerInfo = TypeRegistry.registerTypeInfo(owner.getClass());
            if (ownerInfo.pojoInfo != null) {
                PropertyInfo property = ownerInfo.pojoInfo.getPropertyNoAlias(key);
                if (property != null && property.gettable) codec = property.valueInfo;
            }
        }

        if (codec == null) {
            Class<?> valueClass = value.getClass();
            if (NodeKind.plainOf(valueClass) == NodeKind.UNKNOWN) {
                TypeInfo valueInfo = TypeRegistry.registerTypeInfo(valueClass);
                if (valueInfo.valueSet != null) {
                    codec = valueInfo.requireValueInfo(config.valueFormat(valueClass));
                }
            }
        }

        if (codec == null) return value;
        switch (codec.rawJsonType) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
                return codec.valueToRaw(value);
            default:
                return value;
        }
    }


}
