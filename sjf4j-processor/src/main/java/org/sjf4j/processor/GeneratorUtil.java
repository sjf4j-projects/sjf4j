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
     * Returns true when {@code type} and {@code target} have the same erasure.
     */
    public static boolean isSameErasure(ProcessorContext ctx, TypeMirror type, TypeMirror target) {
        return type != null && target != null && ctx.types.isSameType(ctx.types.erasure(type), ctx.types.erasure(target));
    }

    /**
     * Returns true for generated/native JsonObject subclasses, excluding JsonObject itself.
     */
    public static boolean isJojoType(ProcessorContext ctx, TypeMirror type) {
        return isAssignableErasure(ctx, type, ctx.jsonObjectType) && !isSameErasure(ctx, type, ctx.jsonObjectType);
    }

    /**
     * Returns true for generated/native JsonArray subclasses, excluding JsonArray itself.
     */
    public static boolean isJajoType(ProcessorContext ctx, TypeMirror type) {
        return isAssignableErasure(ctx, type, ctx.jsonArrayType) && !isSameErasure(ctx, type, ctx.jsonArrayType);
    }

    /**
     * Returns true when the element represents a Java record type.
     */
    public static boolean isRecord(TypeElement type) {
        return type != null && "RECORD".equals(type.getKind().name());
    }

    /**
     * Builds a generated-code index expression, supporting negative indexes relative to size.
     */
    public static String indexExpr(int index, String sizeExpr) {
        if (index >= 0) return Integer.toString(index);
        if (index == Integer.MIN_VALUE) return sizeExpr + " + " + index;
        return sizeExpr + " - " + (-index);
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
        String explicit = explicitNodePropertyName(element);
        return explicit == null ? fallback : explicit;
    }

    /**
     * Returns the explicit OBNT-facing property name declared by SJF4J/Jackson/Fastjson2
     * property naming annotations, or null when no explicit non-empty name is present.
     */
    public static String explicitNodePropertyName(Element element) {
        NodeProperty property = element.getAnnotation(NodeProperty.class);
        if (property != null && property.value().length() != 0) return property.value();
        String name = _annotationString(element, "tools.jackson.annotation.JsonProperty", "value");
        if (name != null && name.length() != 0) return name;
        name = _annotationString(element, "com.fasterxml.jackson.annotation.JsonProperty", "value");
        if (name != null && name.length() != 0) return name;
        name = _annotationString(element, "com.alibaba.fastjson2.annotation.JSONField", "name");
        if (name != null && name.length() != 0) return name;
        return null;
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
        if (isObject(ctx, current)) {
            return ctx.objectType;
        }
        TypeElement type = asTypeElement(current);
        if (type != null && isJojoType(ctx, current)) {
            ExecutableElement getter = findJojoReadable(ctx, type, current, name);
            if (getter != null) {
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) current, getter);
                return mt.getReturnType();
            }
            VariableElement field = findJojoReadableField(ctx, type, name);
            if (field != null) {
                return ctx.types.asMemberOf((DeclaredType) current, field);
            }
            if (findJojoWritable(ctx, type, current, name) != null || findJojoWritableField(ctx, type, name) != null) return null;
            return ctx.objectType;
        }
        if (isAssignableErasure(ctx, current, ctx.jsonObjectType)) return ctx.objectType;
        if (isAssignableErasure(ctx, current, ctx.mapType)) {
            return mapValueType(ctx, current);
        }
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
     * record accessors, and explicitly property-annotated methods.
     */
    public static ExecutableElement findReadable(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        if (name.length() == 0) return null;
        for (int pass = 0; pass < 2; pass++) {
            boolean explicitOnly = pass == 0;
            for (Element member : ctx.elements.getAllMembers(type)) {
                if (member.getKind() != ElementKind.METHOD) continue;
                Set<Modifier> modifiers = member.getModifiers();
                if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
                ExecutableElement method = (ExecutableElement) member;
                if (method.getParameters().size() != 0) continue;
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
                TypeMirror returnType = mt.getReturnType();
                if (returnType.getKind() == TypeKind.VOID) continue;
                if (!explicitOnly && explicitNodePropertyName(method) != null) continue;
                String property = explicitOnly ? explicitNodePropertyName(method) : readablePropertyBase(ctx, type, owner, method);
                if (name.equals(property)) return method;
            }
        }
        return null;
    }

    /**
     * Finds a public readable field by Java name first, then by @NodeProperty name.
     */
    public static VariableElement findReadableField(ProcessorContext ctx, TypeElement type, String name) {
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
        for (int pass = 0; pass < 2; pass++) {
            boolean explicitOnly = pass == 0;
            for (Element member : ctx.elements.getAllMembers(type)) {
                if (member.getKind() != ElementKind.METHOD) continue;
                Set<Modifier> modifiers = member.getModifiers();
                if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
                ExecutableElement method = (ExecutableElement) member;
                if (method.getParameters().size() != 1) continue;
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
                if (mt.getReturnType().getKind() != TypeKind.VOID) continue;
                String base = writablePropertyBase(method);
                if (base == null) continue;
                if (!explicitOnly && explicitNodePropertyName(method) != null) continue;
                String property = explicitOnly ? explicitNodePropertyName(method) : base;
                if (name.equals(property)) return method;
            }
        }
        return null;
    }

    /**
     * Finds a public writable field (non-final) by Java name first, then by @NodeProperty name.
     */
    public static VariableElement findWritableField(ProcessorContext ctx, TypeElement type, String name) {
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

    /** Finds a JOJO readable method while ignoring inherited JsonObject/Object API methods. */
    public static ExecutableElement findJojoReadable(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        if (name.length() == 0) return null;
        for (int pass = 0; pass < 2; pass++) {
            boolean explicitOnly = pass == 0;
            if (!explicitOnly && hasExplicitJojoReadable(ctx, type, owner, name)) return null;
            for (Element member : ctx.elements.getAllMembers(type)) {
                if (member.getKind() != ElementKind.METHOD || isJsonBaseMember(member)) continue;
                Set<Modifier> modifiers = member.getModifiers();
                if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
                ExecutableElement method = (ExecutableElement) member;
                if (method.getParameters().size() != 0) continue;
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
                TypeMirror returnType = mt.getReturnType();
                if (returnType.getKind() == TypeKind.VOID) continue;
                if (!explicitOnly && explicitNodePropertyName(method) != null) continue;
                String property = explicitOnly ? explicitNodePropertyName(method) : readablePropertyBase(ctx, type, owner, method);
                if (name.equals(property)) return method;
            }
        }
        return null;
    }

    /** Finds a JOJO readable field while ignoring inherited JsonObject/Object fields. */
    public static VariableElement findJojoReadableField(ProcessorContext ctx, TypeElement type, String name) {
        for (int pass = 0; pass < 2; pass++) {
            boolean explicitOnly = pass == 0;
            if (!explicitOnly && hasExplicitJojoReadable(ctx, type, null, name)) return null;
            for (Element member : ctx.elements.getAllMembers(type)) {
                if (member.getKind() != ElementKind.FIELD || isJsonBaseMember(member)) continue;
                Set<Modifier> modifiers = member.getModifiers();
                if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
                if (_matchesPropertyName(member, member.getSimpleName().toString(), name, explicitOnly)) {
                    return (VariableElement) member;
                }
            }
        }
        return null;
    }

    /** Finds a JOJO writable method while ignoring inherited JsonObject/Object API methods. */
    public static ExecutableElement findJojoWritable(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        if (name.length() == 0) return null;
        for (int pass = 0; pass < 2; pass++) {
            boolean explicitOnly = pass == 0;
            if (!explicitOnly && hasExplicitJojoWritable(ctx, type, owner, name)) return null;
            for (Element member : ctx.elements.getAllMembers(type)) {
                if (member.getKind() != ElementKind.METHOD || isJsonBaseMember(member)) continue;
                Set<Modifier> modifiers = member.getModifiers();
                if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
                ExecutableElement method = (ExecutableElement) member;
                if (method.getParameters().size() != 1) continue;
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
                if (mt.getReturnType().getKind() != TypeKind.VOID) continue;
                String base = writablePropertyBase(method);
                if (base != null && _matchesPropertyName(method, base, name, explicitOnly)) return method;
            }
        }
        return null;
    }

    /** Finds a JOJO writable field while ignoring inherited JsonObject/Object fields. */
    public static VariableElement findJojoWritableField(ProcessorContext ctx, TypeElement type, String name) {
        VariableElement field = findJojoReadableField(ctx, type, name);
        if (field != null && !field.getModifiers().contains(Modifier.FINAL)) return field;
        return null;
    }

    /**
     * Returns true for inherited JsonObject/JsonContainer/Object members that are not JOJO node properties.
     */
    public static boolean isJsonBaseMember(Element member) {
        Element owner = member.getEnclosingElement();
        if (!(owner instanceof TypeElement)) return false;
        String name = ((TypeElement) owner).getQualifiedName().toString();
        return name.equals("org.sjf4j.JsonObject") || name.equals("org.sjf4j.JsonContainer") || name.equals("java.lang.Object");
    }

    private static boolean hasExplicitJojoReadable(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (isJsonBaseMember(member)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            if (member.getKind() == ElementKind.FIELD) {
                if (_matchesPropertyName(member, member.getSimpleName().toString(), name, true)) return true;
            } else if (member.getKind() == ElementKind.METHOD && owner != null) {
                ExecutableElement method = (ExecutableElement) member;
                if (!method.getParameters().isEmpty()) continue;
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
                if (mt.getReturnType().getKind() == TypeKind.VOID) continue;
                String explicit = explicitNodePropertyName(method);
                if (explicit != null && explicit.equals(name)) return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitJojoWritable(ProcessorContext ctx, TypeElement type, TypeMirror owner, String name) {
        for (Element member : ctx.elements.getAllMembers(type)) {
            if (isJsonBaseMember(member)) continue;
            Set<Modifier> modifiers = member.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) continue;
            if (member.getKind() == ElementKind.FIELD) {
                if (!modifiers.contains(Modifier.FINAL) && _matchesPropertyName(member, member.getSimpleName().toString(), name, true)) return true;
            } else if (member.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) member;
                if (method.getParameters().size() != 1) continue;
                ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
                if (mt.getReturnType().getKind() != TypeKind.VOID) continue;
                String base = writablePropertyBase(method);
                if (base != null && _matchesPropertyName(method, base, name, true)) return true;
            }
        }
        return false;
    }

    private static boolean _matchesPropertyName(Element member, String fallback, String name, boolean explicitOnly) {
        String explicit = explicitNodePropertyName(member);
        if (explicit != null) return explicit.equals(name);
        return !explicitOnly && fallback != null && fallback.equals(name);
    }

    /**
     * Returns the property base for a public zero-arg readable method.
     * JavaBean getters map to their bean suffix; record accessors map to the method name.
     * Ordinary fluent methods do not count unless they have an explicit property annotation.
     */
    public static String readablePropertyBase(ProcessorContext ctx, TypeElement type, TypeMirror owner, ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        if (methodName.equals("getClass")) return null;
        if (isRecordAccessor(type, methodName)) return methodName;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decap(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            ExecutableType mt = (ExecutableType) ctx.types.asMemberOf((DeclaredType) owner, method);
            TypeMirror returnType = mt.getReturnType();
            if (returnType.getKind() == TypeKind.BOOLEAN ||
                    ctx.types.isSameType(ctx.types.erasure(returnType), ctx.types.erasure(ctx.elements.getTypeElement("java.lang.Boolean").asType()))) {
                return decap(methodName.substring(2));
            }
            return null;
        }
        return null;
    }

    private static boolean isRecordAccessor(TypeElement type, String methodName) {
        if (!isRecord(type)) return false;
        for (Element element : type.getEnclosedElements()) {
            if ("RECORD_COMPONENT".equals(element.getKind().name()) && element.getSimpleName().contentEquals(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the property base for a public single-argument setter method.
     */
    public static String writablePropertyBase(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        if (methodName.startsWith("set") && methodName.length() > 3) return decap(methodName.substring(3));
        return null;
    }

    /**
     * Returns the readable property name for a method, with explicit annotations taking precedence.
     */
    public static String readablePropertyName(ProcessorContext ctx, TypeElement type, TypeMirror owner, ExecutableElement method) {
        String explicit = explicitNodePropertyName(method);
        return explicit == null ? readablePropertyBase(ctx, type, owner, method) : explicit;
    }
}
