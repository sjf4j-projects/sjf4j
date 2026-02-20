package org.sjf4j.path;


import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;

import java.util.Objects;

@NodeValue
public class JsonPointer extends JsonPath {

    protected JsonPointer(JsonPointer pointer) {
        super(pointer);
    }

    protected JsonPointer(String raw, PathSegment[] segments) {
        super(raw, segments);
    }

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
            throw new IllegalArgumentException("JSON Pointer expr must start with '/'");
        }
        return new JsonPointer(expr, segments);
    }

    public static JsonPointer fromLast(PathSegment lastSegment) {
        Objects.requireNonNull(lastSegment, "lastSegment is null");
        PathSegment[] segments = Paths.linearize(lastSegment);
        return new JsonPointer(null, segments);
    }

//    static {
//        NodeRegistry.registerValueCodec(JsonPointer.class);
//    }

    @ValueToRaw
    @Override
    public String toExpr() {
        return toPointerExpr();
    }

    @Override
    public String toString() {
        return toPointerExpr();
    }

    @ValueCopy
    public JsonPointer copy() {
        return new JsonPointer(this);
    }

}
