package org.sjf4j.node;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;


public final class TypedNode {

    private final Object node;
    private final Type clazzType;

    private TypedNode(Object node, Type clazzType) {
        this.node = node;
        this.clazzType = clazzType;
    }

    public Object getNode() {
        return node;
    }

    public Type getClazzType() {
        return clazzType;
    }


    // ----------- Factory Methods -----------

    public static TypedNode of(Object node, Type type) {
        NodeType nt = NodeType.of(node);

        return new TypedNode(node, type);
    }

    public static TypedNode infer(Object node) {
        return new TypedNode(node, node == null ? Object.class : node.getClass());
    }

    public static TypedNode nullOf(Type type) {
        return new TypedNode(null, type);
    }

    // ----------- Utility Methods -----------

    public boolean isNull() {
        return node == null;
    }

    public boolean isParameterized() {
        return clazzType instanceof ParameterizedType;
    }

    public Class<?> getRawClass() {
        return Types.getRawClass(clazzType);
    }

}
