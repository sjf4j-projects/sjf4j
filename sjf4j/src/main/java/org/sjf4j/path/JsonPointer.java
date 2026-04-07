package org.sjf4j.path;


import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.exception.JsonException;

import java.util.Arrays;
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
        Objects.requireNonNull(expr, "expr");
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
        Objects.requireNonNull(lastSegment, "lastSegment");
        PathSegment[] segments = Paths.linearize(lastSegment);
        return new JsonPointer(null, segments);
    }

    /**
     * Returns parent pointer, or {@code null} when this pointer is root.
     */
    public JsonPointer parent() {
        if (segments.length <= 1) return null;
        PathSegment[] parentSegs = new PathSegment[segments.length - 1];
        System.arraycopy(segments, 0, parentSegs, 0, parentSegs.length);
        return new JsonPointer(null, parentSegs);
    }

    /**
     * Returns true when this pointer ends with append token {@code /-}.
     */
    public boolean isAppend() {
        if (segments.length <= 1) return false;
        return segments[segments.length - 1] instanceof PathSegment.Append;
    }

    /**
     * Returns child pointer with one array index segment appended.
     */
    public JsonPointer childIndex(int index) {
        PathSegment[] childSegs = Arrays.copyOf(segments, segments.length + 1);
        PathSegment parent = segments[segments.length - 1];
        childSegs[segments.length] = new PathSegment.Index(parent, null, index);
        return new JsonPointer(null, childSegs);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        JsonPointer other = (JsonPointer) obj;
        if (segments.length != other.segments.length) return false;
        for (int i = 0; i < segments.length; i++) {
            PathSegment a = segments[i];
            PathSegment b = other.segments[i];
            if (a.getClass() != b.getClass()) return false;
            if (a instanceof PathSegment.Name) {
                if (!((PathSegment.Name) a).name.equals(((PathSegment.Name) b).name)) return false;
            } else if (a instanceof PathSegment.Index) {
                PathSegment.Index ai = (PathSegment.Index) a;
                PathSegment.Index bi = (PathSegment.Index) b;
                String at = ai.pointerToken != null ? ai.pointerToken : Integer.toString(ai.index);
                String bt = bi.pointerToken != null ? bi.pointerToken : Integer.toString(bi.index);
                if (!at.equals(bt)) return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (PathSegment seg : segments) {
            int segHash = seg.getClass().hashCode();
            if (seg instanceof PathSegment.Name) {
                segHash = 31 * segHash + ((PathSegment.Name) seg).name.hashCode();
            } else if (seg instanceof PathSegment.Index) {
                PathSegment.Index index = (PathSegment.Index) seg;
                String token = index.pointerToken != null ? index.pointerToken : Integer.toString(index.index);
                segHash = 31 * segHash + token.hashCode();
            }
            hash = 31 * hash + segHash;
        }
        return hash;
    }
    
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
