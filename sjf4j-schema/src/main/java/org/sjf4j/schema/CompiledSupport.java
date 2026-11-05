package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.NodeKind;
import org.sjf4j.RuntimeContext;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.PathSyntax;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.util.Asserts;
import org.sjf4j.value.ValueInfo;

import java.util.Map;

/**
 * Runtime support for generated compiled JSON Schema validators.
 * Only operations that cannot be emitted as direct Java access belong here.
 */
public final class CompiledSupport {
    private CompiledSupport() {}

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
     * Materializes a diagnostic instance pointer only on a failed assertion.
     * JSON Pointer's '-' append token is an ordinary object member name in
     * instance locations, so replace it without changing JSON Patch parsing.
     */
    public static PathSegment instancePath(String pointer) {
        PathSegment last = JsonPointer.parse(pointer).tail();
        boolean hasAppend = false;
        for (PathSegment segment = last; segment != null; segment = segment.parent()) {
            if (segment instanceof PathSegment.Append) {
                hasAppend = true;
                break;
            }
        }
        if (!hasAppend) return last;

        PathSegment result = PathSegment.Root.INSTANCE;
        PathSegment[] segments = PathSyntax.linearize(last);
        for (int i = 1; i < segments.length; i++) {
            PathSegment segment = segments[i];
            if (segment instanceof PathSegment.Append) {
                result = new PathSegment.Name(result, "-");
            } else if (segment instanceof PathSegment.Name) {
                result = new PathSegment.Name(result, ((PathSegment.Name) segment).name);
            } else if (segment instanceof PathSegment.Index) {
                result = new PathSegment.Index(result, ((PathSegment.Index) segment).index);
            } else {
                throw new IllegalStateException("Unsupported diagnostic pointer segment: " + segment);
            }
        }
        return result;
    }

    /**
     * Reads a member for generated schema validation using its scalar raw
     * ValueCodec representation. Property-level formats take precedence over
     * the runtime context's format selection.
     *
     * <p>Map/List-encoded value nodes remain atomic: their raw containers must
     * not be traversed as structural schema objects or arrays.
     */
    public static Object scalarValueInObject(Object owner, String key, RuntimeContext context) {
        Asserts.notNull(context, "context");
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
                    codec = valueInfo.requireValueInfo(context.valueFormat(valueClass));
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
