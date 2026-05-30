package org.sjf4j.processor.generate;

import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import java.util.List;
import java.util.Set;

final class GeneratorUtil {
    private GeneratorUtil() {}

    /**
     * Returns true when {@code type}'s erasure can be assigned to {@code target}'s erasure.
     */
    static boolean isAssignableErasure(ProcessorContext ctx, TypeMirror type, TypeMirror target) {
        return type != null && target != null && ctx.types.isAssignable(ctx.types.erasure(type), ctx.types.erasure(target));
    }

    /**
     * Returns the type element represented by a declared type mirror, or null for non-declared types.
     */
    static TypeElement asTypeElement(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    /**
     * Resolves a map value type, falling back to Object when the type argument is unavailable.
     */
    static TypeMirror mapValueType(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() == 2) return concrete(ctx, args.get(1));
        }
        return ctx.objectType;
    }

    /**
     * Resolves a list element type, falling back to Object when the type argument is unavailable.
     */
    static TypeMirror listValueType(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() == 1) return concrete(ctx, args.get(0));
        }
        return ctx.objectType;
    }

    /**
     * Converts wildcard types to their extends bound, or Object for unbounded/super wildcards.
     */
    static TypeMirror concrete(ProcessorContext ctx, TypeMirror type) {
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            return wildcard.getExtendsBound() == null ? ctx.objectType : wildcard.getExtendsBound();
        }
        return type;
    }

    /**
     * Returns the generated-source type name for a mirror.
     */
    static String typeName(TypeMirror type) { return type.toString(); }

    /**
     * Returns a local variable type name, boxing primitives and resolving wildcards.
     */
    static String localTypeName(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return ctx.types.boxedClass((javax.lang.model.type.PrimitiveType) type).getQualifiedName().toString();
        }
        return typeName(concrete(ctx, type));
    }

    /**
     * Returns a class literal using the type erasure.
     */
    static String classLiteral(ProcessorContext ctx, TypeMirror type) {
        return ctx.types.erasure(type).toString() + ".class";
    }

    /**
     * Boxes primitive types and resolves wildcard types for generated code use.
     */
    static TypeMirror boxed(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return ctx.types.boxedClass((javax.lang.model.type.PrimitiveType) type).asType();
        }
        return concrete(ctx, type);
    }

    /**
     * Returns true when the type erases to java.lang.Object.
     */
    static boolean isObject(ProcessorContext ctx, TypeMirror type) {
        return type != null && ctx.types.isSameType(ctx.types.erasure(type), ctx.types.erasure(ctx.objectType));
    }

    /**
     * Escapes a string for insertion into generated Java string literals.
     */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Finds a public, non-static, zero-argument readable method with the exact supplied method name.
     * Getter-name composition such as getX/isX is handled by callers before invoking this method.
     */
    static ExecutableElement findGetter(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name, boolean booleanOnly) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            if (!member.getSimpleName().contentEquals(name)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (!method.getParameters().isEmpty()) continue;
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
            TypeMirror returnType = mt.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) continue;
            if (booleanOnly && returnType.getKind() != TypeKind.BOOLEAN &&
                    !ctx.types.isSameType(ctx.types.erasure(returnType), ctx.types.erasure(ctx.elements.getTypeElement("java.lang.Boolean").asType()))) {
                continue;
            }
            return method;
        }
        return null;
    }

    /**
     * Finds a public, non-static, single-argument void method with the exact supplied setter name.
     */
    static ExecutableElement findSetter(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            if (!member.getSimpleName().contentEquals(name)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (method.getParameters().size() != 1) continue;
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
            if (mt.getReturnType().getKind() != TypeKind.VOID) continue;
            return method;
        }
        return null;
    }

    /**
     * Finds a public, non-static field with the exact supplied field name.
     */
    static VariableElement findField(ProcessorContext ctx, TypeElement type, String name) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.FIELD) continue;
            if (!member.getSimpleName().contentEquals(name)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.STATIC)) return (VariableElement) member;
        }
        return null;
    }
}
