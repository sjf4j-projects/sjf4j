package org.sjf4j.path;


import org.sjf4j.annotation.node.ValueToRaw;
import org.sjf4j.annotation.node.ValueCopy;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.exception.NodeException;
import org.sjf4j.util.Asserts;

import java.util.Arrays;

/**
 * JSON Pointer wrapper for JSON Patch paths and single-location OBNT access.
 *
 * <p>This class is a restricted {@link JsonPath}. It accepts the empty root
 * pointer and slash-delimited tokens with RFC 6901 escaping. Nonnegative
 * 32-bit integer tokens select an array index for array node parents and
 * retain their token as an object key for object node parents. Likewise,
 * the terminal {@code -} selects an object member named {@code "-"} or the
 * append position after an array's last element. Array append positions are
 * only writable, not readable. Nonterminal {@code -} tokens are object names.
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
     * Parses the supported JSON Pointer subset. A {@code null} expression maps
     * to {@code null}; the empty string denotes root; every non-root expression
     * must start with {@code /}. Unlike {@link JsonPath#parse(String)}, this
     * method does not trim the expression, since whitespace can be part of a token.
     */
    @RawToValue
    public static JsonPointer parse(String expr) {
        if (expr == null) return null;

        PathSegment[] segments;
        if (expr.isEmpty()) {
            segments = new PathSegment[]{PathSegment.Root.INSTANCE};
        } else if (expr.startsWith("/")) {
            segments = PathSyntax.parsePointer(expr);
        } else {
            throw new NodeException("invalid JSON Pointer expression '" + expr + "': must start with '/'");
        }
        return new JsonPointer(expr, segments);
    }

    /**
     * Creates a pointer from the last segment in a chain.
     */
    public static JsonPointer fromLast(PathSegment lastSegment) {
        Asserts.notNull(lastSegment, "lastSegment");
        PathSegment[] segments = PathSyntax.linearize(lastSegment);
        return new JsonPointer(null, segments);
    }

    /**
     * Returns parent pointer, or {@code null} when this pointer is root.
     */
    public JsonPointer parent() {
        if (segments.length <= 1) return null;
        PathSegment[] parentSegs = new PathSegment[segments.length - 1];
        System.arraycopy(segments, 0, parentSegs, 0, parentSegs.length);
        PathSegment last = parentSegs[parentSegs.length - 1];
        if (last instanceof PathSegment.Name && "-".equals(((PathSegment.Name) last).name)) {
            parentSegs[parentSegs.length - 1] = new PathSegment.Append(last.parent(), "-");
        }
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
     * Returns a child pointer with one array index segment appended. The index
     * is not validated here; parsed JSON Pointers recognize nonnegative indexes.
     */
    public JsonPointer childIndex(int index) {
        PathSegment[] childSegs = Arrays.copyOf(segments, segments.length + 1);
        PathSegment parent = segments[segments.length - 1];
        childSegs[segments.length] = new PathSegment.Index(parent, index);
        return new JsonPointer(null, childSegs);
    }


    /**
     * Pointer identity depends on RFC 6901 tokens, not their internal segment types.
     * For example, Name("0") and Index(0) both represent the pointer "/0".
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        return toPointerExpr().equals(((JsonPointer) obj).toPointerExpr());
    }

    @Override
    public int hashCode() {
        return toPointerExpr().hashCode();
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
