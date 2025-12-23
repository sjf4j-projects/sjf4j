package org.sjf4j.path;


import org.sjf4j.annotation.convertible.Convert;
import org.sjf4j.annotation.convertible.Copy;
import org.sjf4j.annotation.convertible.NodeConvertible;
import org.sjf4j.annotation.convertible.Unconvert;
import org.sjf4j.node.NodeRegistry;

@NodeConvertible
public class JsonPointer extends JsonPath {

    public JsonPointer() {
        super();
    }

    protected JsonPointer(JsonPointer pointer) {
        super(pointer);
    }

    protected JsonPointer(String expr) {
        super(expr);
    }

    @Unconvert
    public static JsonPointer compile(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expr must not be null");
        if (expr.startsWith("/")) {
            return new JsonPointer(expr);
        } else {
            throw new IllegalArgumentException("JSON Pointer expr must start with '/'");
        }
    }

    static {
        NodeRegistry.registerConvertible(JsonPointer.class);
    }

    @Convert
    @Override
    public String toExpr() {
        return toPointerExpr();
    }

    @Override
    public String toString() {
        return toPointerExpr();
    }

    @Copy
    public JsonPointer copy() {
        return new JsonPointer(this);
    }

}
