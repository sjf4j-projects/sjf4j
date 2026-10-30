package org.sjf4j.processor.schema;

import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Compile-time representation of a Java input value. The compiler decides
 * whether a JSON shape can be handled natively; expressions are emitted later.
 */
final class SchemaValue {
    enum Shape { STRING, NUMBER, BOOLEAN, COLLECTION, ARRAY, DYNAMIC, OTHER }

    final TypeMirror type;
    final Shape shape;
    final boolean nullable;
    final boolean enumType;
    final boolean uuidType;

    private SchemaValue(TypeMirror type, Shape shape, boolean enumType) {
        this.type = type;
        this.shape = shape;
        this.enumType = enumType;
        this.uuidType = type != null && "java.util.UUID".equals(type.toString());
        this.nullable = type == null || !type.getKind().isPrimitive();
    }

    static SchemaValue of(ProcessorContext context, TypeMirror type) {
        if (type == null || "java.lang.Object".equals(type.toString())) {
            return new SchemaValue(type, Shape.DYNAMIC, false);
        }
        String name = type.toString();
        if ("java.util.UUID".equals(name)) return new SchemaValue(type, Shape.STRING, false);
        if ("boolean".equals(name) || "java.lang.Boolean".equals(name)) return new SchemaValue(type, Shape.BOOLEAN, false);
        if ("java.lang.String".equals(name) || "java.lang.Character".equals(name)
                || "char".equals(name)) {
            return new SchemaValue(type, Shape.STRING, false);
        }
        switch (name) {
            case "byte": case "short": case "int": case "long":
            case "float": case "double":
            case "java.lang.Byte": case "java.lang.Short":
            case "java.lang.Integer": case "java.lang.Long":
            case "java.lang.Float": case "java.lang.Double":
            case "java.math.BigDecimal": case "java.math.BigInteger":
                return new SchemaValue(type, Shape.NUMBER, false);
            default:
                break;
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return new SchemaValue(type, Shape.ARRAY, false);
        }
        if (type instanceof DeclaredType) {
            String erased = context.typeUtils.erasure(type).toString();
            if ("java.util.List".equals(erased) || "java.util.Set".equals(erased)
                    || "java.util.Collection".equals(erased)) {
                return new SchemaValue(type, Shape.COLLECTION, false);
            }
        }
        TypeElement element = context.types.typeElement(type);
        if (element != null && element.getKind() == ElementKind.ENUM) {
            return new SchemaValue(type, Shape.STRING, true);
        }
        return new SchemaValue(type, Shape.OTHER, false);
    }

    boolean isArrayLike() {
        return shape == Shape.COLLECTION || shape == Shape.ARRAY;
    }

    TypeMirror itemType() {
        if (shape == Shape.ARRAY) return ((ArrayType) type).getComponentType();
        if (shape == Shape.COLLECTION) {
            List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
            return arguments.size() == 1 ? arguments.get(0) : null;
        }
        return null;
    }

    String localType() {
        return shape == Shape.STRING || shape == Shape.NUMBER || shape == Shape.BOOLEAN || isArrayLike()
                ? type.toString() : "Object";
    }
}
