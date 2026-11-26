package org.sjf4j.processor.code;

import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/** Java source expressions only; no operations are added to generated runtime code. */
public final class JavaExpressions {

    private JavaExpressions() {
    }

    public static String concreteType(TypeSystem types, TypeMirror type) {
        return types.concrete(type).toString();
    }

    public static String boxedType(TypeSystem types, TypeMirror type) {
        return types.boxed(type).toString();
    }

    public static String boxedRawType(TypeSystem types, Types javacTypes, TypeMirror type) {
        return javacTypes.erasure(types.boxed(type)).toString();
    }

    public static String boxedClassLiteral(TypeSystem types, Types javacTypes, TypeMirror type) {
        return boxedRawType(types, javacTypes, type) + ".class";
    }

    /** Keep the caller's cast/boxing policy explicit. */
    public static String cast(String type, String expression) {
        return "(" + type + ") " + expression;
    }

    public static String newInstance(String type, boolean diamond) {
        return "new " + type + (diamond ? "<>()" : "()");
    }
}
