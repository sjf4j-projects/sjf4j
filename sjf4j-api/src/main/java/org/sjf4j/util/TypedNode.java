package org.sjf4j.util;

import lombok.Getter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@Getter
public final class TypedNode {

    private final Object node;
    private final Type type;

    public TypedNode(Object node, Type type) {
        this.node = node;
        this.type = type;
    }

    // ----------- Factory Methods -----------

    public static TypedNode of(Object node, Type type) {
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
        return type instanceof ParameterizedType;
    }

    public Class<?> getRawClass() {
        return TypeUtil.getRawClass(type);
    }

    @Override
    public String toString() {
        return "TypedObject{node=" + node + ", type=" + type.getTypeName() + '}';
    }


}
