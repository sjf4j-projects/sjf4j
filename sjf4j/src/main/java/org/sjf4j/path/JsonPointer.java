package org.sjf4j.path;


import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.exception.JsonException;

import java.util.Objects;

/**
 * JSON Pointer wrapper for JSON Patch paths.
 *
 * <p>This class is a restricted {@link JsonPath} that only accepts
 * RFC 6901 pointer syntax (starts with '/').
 */
@NodeValue
public class JsonPointer extends JsonPath {

    /**
     * Creates a pointer by copying another pointer.
     */
    protected JsonPointer(JsonPointer pointer) {
        super(pointer);
    }

    /**
     * Creates a pointer from raw expression and parsed segments.
     */
    protected JsonPointer(String raw, PathSegment[] segments) {
        super(raw, segments);
    }

    /**
     * Compiles a JSON Pointer expression (RFC 6901).
     */
    @RawToValue
    public static JsonPointer compile(String expr) {
        Objects.requireNonNull(expr, "expr is null");
        expr = expr.trim();

        PathSegment[] segments;
        if (expr.isEmpty()) {
            segments = new PathSegment[]{PathSegment.Root.INSTANCE};
        } else if (expr.startsWith("/")) {
            segments = Paths.parsePointer(expr);
        } else {
            throw new JsonException("Invalid JSON Pointer expression '" + expr + "': must start with '/'");
        }
        return new JsonPointer(expr, segments);
    }

    /**
     * Creates a pointer from the last segment in a chain.
     */
    public static JsonPointer fromLast(PathSegment lastSegment) {
        Objects.requireNonNull(lastSegment, "lastSegment is null");
        PathSegment[] segments = Paths.linearize(lastSegment);
        return new JsonPointer(null, segments);
    }

//    static {
//        NodeRegistry.registerValueCodec(JsonPointer.class);
//    }

    /**
     * Returns this pointer as an RFC 6901 expression.
     */
    @ValueToRaw
    @Override
    public String toExpr() {
        return toPointerExpr();
    }

    /**
     * Returns this pointer as an RFC 6901 expression.
     */
    @Override
    public String toString() {
        return toPointerExpr();
    }

    /**
     * Returns a copy of this pointer.
     */
    @ValueCopy
    public JsonPointer copy() {
        return new JsonPointer(this);
    }

}
