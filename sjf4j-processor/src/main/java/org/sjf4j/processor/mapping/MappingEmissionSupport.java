package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.CompilationContext;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;

/** Small source-generation helpers shared by mapping emitters. */
final class MappingEmissionSupport {

    private MappingEmissionSupport() {
    }

    /** Default for an absent source read feeding a primitive constructor parameter. */
    static String primitiveDefault(TypeKind kind) {
        switch (kind) {
            case BOOLEAN: return "false";
            case CHAR: return "'\\0'";
            case LONG: return "0L";
            case FLOAT: return "0.0f";
            case DOUBLE: return "0.0d";
            case BYTE:
            case SHORT:
            case INT: return "0";
            default: throw new IllegalArgumentException("not a primitive value: " + kind);
        }
    }

    static boolean rootKnownNonNull(
            MappingPlan plan) {

        return plan.sources()
                .size() == 1;
    }

    static String sizeExpression(String owner, NodeAccess.Kind kind) {
        switch (kind) {
            case ARRAY:
                return owner + ".length";

            case LIST:
            case JSON_ARRAY:
                return owner + ".size()";

            default:
                return null;
        }
    }

    static boolean isMapLike(NodeKind kind) {
        switch (kind) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
                return true;

            default:
                return false;
        }
    }

    static void error(CompilationContext context, ExecutableElement method, GeneratedClass generated, String message) {
        generated.invalidate();
        context.error(method, generated.originName() + ": " + message);
    }

}
