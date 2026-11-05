package org.sjf4j.schema;

import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.meta.PropertyInfo;
import org.sjf4j.meta.TypeInfo;
import org.sjf4j.meta.TypeRegistry;
import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathSegment;
import org.sjf4j.path.PathSyntax;
import org.sjf4j.value.ValueInfo;

import java.util.Map;

/**
 * Runtime support for generated compiled JSON Schema validators.
 * Only operations that cannot be emitted as direct Java access belong here.
 */
public final class CompiledValidatorSupport {
    private CompiledValidatorSupport() {}

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
     * Reads a structural object member for generated Schema validators.
     * Unlike a plain Nodes.getInObject call, a POJO member can select a
     * nondefault scalar codec through @NodeProperty(valueFormat/valuePattern).
     * Raw Map/List codec values stay atomic leaves, never traversable nodes.
     */
    public static Object objectPropertyValue(Object owner, String key) {
        Object value = Nodes.getInObject(owner, key);
        if (value == null || owner instanceof Map || owner instanceof JsonObject)
            return value;
        TypeInfo info = TypeRegistry.registerTypeInfo(owner.getClass());
        if (info.pojoInfo == null) return value;
        PropertyInfo property = info.pojoInfo.getPropertyNoAlias(key);
        if (property == null || property.valueInfo == null) return value;
        ValueInfo codec = property.valueInfo;
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
