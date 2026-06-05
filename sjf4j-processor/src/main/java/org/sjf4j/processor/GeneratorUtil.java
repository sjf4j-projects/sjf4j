package org.sjf4j.processor;

import org.sjf4j.annotation.node.NodeProperty;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods shared by processor source generators.
 *
 * <p>Kept package-private and static-only to avoid extra generator layers while
 * centralizing type resolution, member lookup, and source-safe name handling.</p>
 */
public final class GeneratorUtil {
    private GeneratorUtil() {}

    public static final String COMPILED_IMPL_POSTFIX = "_Impl";

    /**
     * Returns true when {@code type}'s erasure can be assigned to {@code target}'s erasure.
     */
    public static boolean isAssignableErasure(ProcessorContext ctx, TypeMirror type, TypeMirror target) {
        return type != null && target != null && ctx.types.isAssignable(ctx.types.erasure(type), ctx.types.erasure(target));
    }

    /**
     * Returns the type element represented by a declared type mirror, or null for non-declared types.
     */
    public static TypeElement asTypeElement(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element element = ((DeclaredType) type).asElement();
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    /**
     * Resolves a map value type, falling back to Object when the type argument is unavailable.
     */
    public static TypeMirror mapValueType(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() == 2) return concrete(ctx, args.get(1));
        }
        return ctx.objectType;
    }

    /**
     * Resolves a list element type, falling back to Object when the type argument is unavailable.
     */
    public static TypeMirror listValueType(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() == 1) return concrete(ctx, args.get(0));
        }
        return ctx.objectType;
    }

    /**
     * Converts wildcard types to their extends bound, or Object for unbounded/super wildcards.
     */
    public static TypeMirror concrete(ProcessorContext ctx, TypeMirror type) {
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            return wildcard.getExtendsBound() == null ? ctx.objectType : wildcard.getExtendsBound();
        }
        return type;
    }

    /**
     * Returns the generated-source type name for a mirror.
     */
    public static String typeName(TypeMirror type) { return type.toString(); }

    /**
     * Returns a local variable type name, boxing primitives and resolving wildcards.
     */
    public static String localTypeName(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return ctx.types.boxedClass((javax.lang.model.type.PrimitiveType) type).getQualifiedName().toString();
        }
        return typeName(concrete(ctx, type));
    }

    /**
     * Returns a class literal using the type erasure.
     */
    public static String classLiteral(ProcessorContext ctx, TypeMirror type) {
        return ctx.types.erasure(type).toString() + ".class";
    }

    /**
     * Returns the OBNT-facing property name declared by SJF4J/Jackson/Fastjson2
     * property naming annotations, or the Java/member fallback when no explicit
     * name is present. Third-party annotations are read through mirrors to avoid
     * compile-time dependencies.
     */
    public static String nodePropertyName(Element element, String fallback) {
        NodeProperty property = element.getAnnotation(NodeProperty.class);
        if (property != null && property.value().length() != 0) return property.value();
        String name = _annotationString(element, "tools.jackson.annotation.JsonProperty", "value");
        if (name != null && name.length() != 0) return name;
        name = _annotationString(element, "com.fasterxml.jackson.annotation.JsonProperty", "value");
        if (name != null && name.length() != 0) return name;
        name = _annotationString(element, "com.alibaba.fastjson2.annotation.JSONField", "name");
        if (name != null && name.length() != 0) return name;
        return fallback;
    }

    private static String _annotationString(Element element, String annotationName, String memberName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            Element annotation = mirror.getAnnotationType().asElement();
            if (!(annotation instanceof TypeElement)) continue;
            if (!((TypeElement) annotation).getQualifiedName().contentEquals(annotationName)) continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : mirror.getElementValues().entrySet()) {
                if (!e.getKey().getSimpleName().contentEquals(memberName)) continue;
                Object value = e.getValue().getValue();
                return value instanceof String ? (String) value : null;
            }
            return null;
        }
        return null;
    }

    /**
     * Boxes primitive types and resolves wildcard types for generated code use.
     */
    public static TypeMirror boxed(ProcessorContext ctx, TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return ctx.types.boxedClass((javax.lang.model.type.PrimitiveType) type).asType();
        }
        return concrete(ctx, type);
    }

    /**
     * Returns true when boxed/erased {@code from} can be assigned to boxed/erased {@code to}.
     */
    public static boolean isAssignableBoxed(ProcessorContext ctx, TypeMirror from, TypeMirror to) {
        return ctx.types.isAssignable(
                ctx.types.erasure(boxed(ctx, from)),
                ctx.types.erasure(boxed(ctx, to)));
    }

    /**
     * Returns true when boxed {@code from} can be assigned to boxed {@code to}, preserving generics.
     */
    public static boolean isAssignableBoxedGeneric(ProcessorContext ctx, TypeMirror from, TypeMirror to) {
        return ctx.types.isAssignable(boxed(ctx, from), boxed(ctx, to));
    }

    /**
     * Returns true when the type erases to java.lang.Object.
     */
    public static boolean isObject(ProcessorContext ctx, TypeMirror type) {
        return type != null && ctx.types.isSameType(ctx.types.erasure(type), ctx.types.erasure(ctx.objectType));
    }

    /**
     * Escapes a string for insertion into generated Java string literals.
     */
    public static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Decapitalizes a bean property suffix using the framework's simple generator convention.
     */
    public static String decap(String value) {
        return value.length() == 0 ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Finds a public, non-static, zero-argument readable method with the exact supplied method name.
     * Getter-name composition such as getX/isX is handled by callers before invoking this method.
     */
    public static ExecutableElement findGetter(ProcessorContext ctx, TypeElement type, TypeMirror owner,
                                               String name, boolean booleanOnly) {
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
    public static ExecutableElement findSetter(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
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
    public static VariableElement findField(ProcessorContext ctx, TypeElement type, String name) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.FIELD) continue;
            if (!member.getSimpleName().contentEquals(name)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.STATIC)) return (VariableElement) member;
        }
        return null;
    }

    /**
     * Resolves the static type reached by reading an object-name segment.
     * Returns null when the property cannot be resolved.
     */
    public static TypeMirror resolveNameType(ProcessorContext ctx, TypeMirror current, String name) {
        if (isObject(ctx, current) || isAssignableErasure(ctx, current, ctx.jsonObjectType)) {
            return ctx.objectType;
        }
        if (isAssignableErasure(ctx, current, ctx.mapType)) {
            return mapValueType(ctx, current);
        }
        TypeElement type = asTypeElement(current);
        if (type == null) return null;

        ExecutableElement getter = findReadable(ctx, type, current, name);
        if (getter != null) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
            return mt.getReturnType();
        }
        VariableElement field = findReadableField(ctx, type, name);
        if (field != null) {
            return ctx.types.asMemberOf((DeclaredType) current, field);
        }
        return null;
    }

    /**
     * Resolves the static element type reached by reading an array-index segment.
     * Returns null when the index cannot be resolved.
     */
    public static TypeMirror resolveIndexType(ProcessorContext ctx, TypeMirror current, int index) {
        if (isObject(ctx, current) || isAssignableErasure(ctx, current, ctx.jsonArrayType)) {
            return ctx.objectType;
        }
        if (current.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) current).getComponentType();
        }
        if (isAssignableErasure(ctx, current, ctx.listType)) {
            return listValueType(ctx, current);
        }
        return null;
    }

    /**
     * Resolves the element type of a container (List, array, Set, JsonArray).
     * Returns Object when the container type cannot be resolved.
     */
    public static TypeMirror resolveElementType(ProcessorContext ctx, TypeMirror containerType) {
        if (isObject(ctx, containerType) || isAssignableErasure(ctx, containerType, ctx.jsonArrayType)) {
            return ctx.objectType;
        }
        if (containerType.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) containerType).getComponentType();
        }
        if (isAssignableErasure(ctx, containerType, ctx.listType)) {
            return listValueType(ctx, containerType);
        }
        return ctx.objectType;
    }

    /**
     * Finds the readable accessor for a POJO property, accepting JavaBean getters,
     * fluent getters, and @NodeProperty-annotated methods.
     */
    public static ExecutableElement findReadable(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        if (name.length() == 0) return null;
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        ExecutableElement getter = findGetter(ctx, type, owner, "get" + suffix, false);
        if (getter == null) getter = findGetter(ctx, type, owner, "is" + suffix, true);
        if (getter == null) getter = findGetter(ctx, type, owner, name, false);
        if (getter != null) return getter;

        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (method.getParameters().size() != 0) continue;
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
            TypeMirror returnType = mt.getReturnType();
            if (returnType.getKind() == TypeKind.VOID) continue;
            String base = _readablePropertyBase(method);
            if (base != null && nodePropertyName(method, base).equals(name)) return method;
        }
        return null;
    }

    /**
     * Finds a public readable field by Java name first, then by @NodeProperty name.
     */
    public static VariableElement findReadableField(ProcessorContext ctx, TypeElement type, String name) {
        VariableElement field = findField(ctx, type, name);
        if (field != null) return field;
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.FIELD) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            if (nodePropertyName(member, member.getSimpleName().toString()).equals(name)) {
                return (VariableElement) member;
            }
        }
        return null;
    }

    /**
     * Finds a public writable accessor (setter) for a POJO property.
     */
    public static ExecutableElement findWritable(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        if (name.length() == 0) return null;
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        ExecutableElement setter = findSetter(ctx, type, owner, "set" + suffix);
        if (setter != null) return setter;
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.METHOD) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            ExecutableElement method = (ExecutableElement) member;
            if (method.getParameters().size() != 1) continue;
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
            if (mt.getReturnType().getKind() != TypeKind.VOID) continue;
            String methodName = method.getSimpleName().toString();
            if (methodName.startsWith("set") && methodName.length() > 3) {
                String base = decap(methodName.substring(3));
                if (nodePropertyName(method, base).equals(name)) return method;
            }
        }
        return null;
    }

    /**
     * Finds a public writable field (non-final) by Java name first, then by @NodeProperty name.
     */
    public static VariableElement findWritableField(ProcessorContext ctx, TypeElement type, String name) {
        VariableElement field = findField(ctx, type, name);
        if (field != null && !field.getModifiers().contains(Modifier.FINAL)) return field;
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (member.getKind() != ElementKind.FIELD) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.FINAL)) continue;
            if (nodePropertyName(member, member.getSimpleName().toString()).equals(name)) {
                return (VariableElement) member;
            }
        }
        return null;
    }

    private static String _readablePropertyBase(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        if (methodName.equals("getClass")) return null;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decap(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decap(methodName.substring(2));
        }
        return methodName;
    }
}
