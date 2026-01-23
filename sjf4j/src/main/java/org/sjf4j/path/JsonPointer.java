package org.sjf4j.path;


import org.sjf4j.annotation.node.Encode;
import org.sjf4j.annotation.node.Copy;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.Decode;
import org.sjf4j.node.NodeRegistry;

@NodeValue
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

    @Decode
    public static JsonPointer compile(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expr must not be null");
        if (expr.isEmpty() || expr.startsWith("/")) {
            return new JsonPointer(expr);
        } else {
            throw new IllegalArgumentException("JSON Pointer expr must start with '/'");
        }
    }

    static {
        NodeRegistry.registerValueCodec(JsonPointer.class);
    }

    @Encode
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
