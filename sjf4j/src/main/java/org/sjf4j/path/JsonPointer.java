package org.sjf4j.path;


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

    public static JsonPointer compile(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expr must not be null");
        if (expr.startsWith("/")) {
            return new JsonPointer(expr);
        } else {
            throw new IllegalArgumentException("JSON Pointer expr must start with '/'");
        }
    }

    @Override
    public String toExpr() {
        return toPointerExpr();
    }

    @Override
    public String toString() {
        return toPointerExpr();
    }

    @Override
    public JsonPointer copy() {
        return new JsonPointer(this);
    }

}
