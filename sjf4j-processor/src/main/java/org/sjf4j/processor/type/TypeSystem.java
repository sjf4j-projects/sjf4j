package org.sjf4j.processor.type;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.JsonType;
import org.sjf4j.NodeKind;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.ValueToRaw;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Shared compile-time Java type system for SJF4J processors.
 */
public final class TypeSystem {

    private static final String JACKSON2_NODE =
            "com.fasterxml.jackson.databind.JsonNode";
    private static final String JACKSON3_NODE =
            "tools.jackson.databind.JsonNode";
    private static final String GSON_NODE =
            "com.google.gson.JsonElement";
    private static final String JSONP_NODE =
            "jakarta.json.JsonValue";


    private final Types typeUtils;
    private final Elements elements;

    private final TypeMirror objectType;
    private final TypeMirror stringType;
    private final TypeMirror characterType;
    private final TypeMirror booleanType;
    private final TypeMirror numberType;
    private final TypeMirror voidType;

    private final TypeMirror mapType;
    private final TypeMirror listType;
    private final TypeMirror setType;

    private final TypeMirror jsonObjectType;
    private final TypeMirror jsonArrayType;

    private final TypeMirror jackson2NodeType;
    private final TypeMirror jackson3NodeType;
    private final TypeMirror gsonNodeType;
    private final TypeMirror jsonpNodeType;


    public TypeSystem(
            Types typeUtils,
            Elements elements) {

        this.typeUtils =
                Objects.requireNonNull(
                        typeUtils,
                        "typeUtils");

        this.elements =
                Objects.requireNonNull(
                        elements,
                        "elements");

        this.objectType =
                requiredType(
                        Object.class.getName());

        this.stringType =
                requiredType(
                        String.class.getName());

        this.characterType =
                requiredType(
                        Character.class.getName());

        this.booleanType =
                requiredType(
                        Boolean.class.getName());

        this.numberType =
                requiredType(
                        Number.class.getName());

        this.voidType =
                requiredType(
                        Void.class.getName());

        this.mapType =
                requiredType(
                        Map.class.getName());

        this.listType =
                requiredType(
                        List.class.getName());

        this.setType =
                requiredType(
                        Set.class.getName());

        this.jsonObjectType =
                requiredType(
                        JsonObject.class.getName());

        this.jsonArrayType =
                requiredType(
                        JsonArray.class.getName());

        this.jackson3NodeType = optionalType(JACKSON3_NODE);
        this.jackson2NodeType = optionalType(JACKSON2_NODE);
        this.gsonNodeType = optionalType(GSON_NODE);
        this.jsonpNodeType = optionalType(JSONP_NODE);
    }


    // -------------------------------------------------------------------------
    // Node type
    // -------------------------------------------------------------------------

    /**
     * Resolves the compile-time OBNT kind of a Java type.
     *
     * <p>{@link Object} is classified as
     * {@link NodeKind#COMPILE_TIME_UNKNOWN}: it may hold any OBNT node at
     * runtime, but its concrete node kind is not known statically.
     * {@link NodeKind#UNKNOWN} is reserved for types that cannot be classified
     * by the compile-time type system.</p>
     */
    public NodeKind nodeKind(TypeMirror type) {
        if (type == null) {
            return NodeKind.UNKNOWN;
        }

        type = concrete(type);
        TypeKind kind = type.getKind();

        if (kind == TypeKind.NULL) {
            return NodeKind.VALUE_NULL;
        }

        if (kind == TypeKind.ARRAY) {
            return NodeKind.ARRAY_ARRAY;
        }

        if (kind.isPrimitive()) {
            if (kind == TypeKind.BOOLEAN) {
                return NodeKind.VALUE_BOOLEAN;
            }
            if (kind == TypeKind.CHAR) {
                return NodeKind.VALUE_STRING_CHARACTER;
            }
            if (kind == TypeKind.VOID) {
                return NodeKind.UNKNOWN;
            }
            return NodeKind.VALUE_NUMBER;
        }

        TypeElement element = typeElement(type);
        if (element == null) {
            return NodeKind.UNKNOWN;
        }

        // SJF4J object nodes

        if (isSameErasure(type, jsonObjectType)) {
            return NodeKind.OBJECT_JSON_OBJECT;
        }
        if (isAssignableErasure(type, jsonObjectType)) {
            return NodeKind.OBJECT_JOJO;
        }

        // SJF4J array nodes

        if (isSameErasure(type, jsonArrayType)) {
            return NodeKind.ARRAY_JSON_ARRAY;
        }
        if (isAssignableErasure(type, jsonArrayType)) {
            return NodeKind.ARRAY_JAJO;
        }

        // Java containers

        if (isAssignableErasure(type, mapType)) {
            return NodeKind.OBJECT_MAP;
        }
        if (isAssignableErasure(type, listType)) {
            return NodeKind.ARRAY_LIST;
        }
        if (isAssignableErasure(type, setType)) {
            return NodeKind.ARRAY_SET;
        }

        // Java values

        if (isSameErasure(type, stringType)) {
            return NodeKind.VALUE_STRING;
        }
        if (isSameErasure(type, characterType)) {
            return NodeKind.VALUE_STRING_CHARACTER;
        }
        if (element.getKind() == ElementKind.ENUM) {
            return NodeKind.VALUE_STRING_ENUM;
        }
        if (isSameErasure(type, booleanType)) {
            return NodeKind.VALUE_BOOLEAN;
        }
        if (isAssignableErasure(type, numberType)) {
            return NodeKind.VALUE_NUMBER;
        }
        if (isSameErasure(type, voidType)) {
            return NodeKind.VALUE_NULL;
        }

        // @NodeValue

        if (isNodeValue(type)) {
            return nodeValueKind(type);
        }

        /*
         * Object and ExternalNode may contain any OBNT node at runtime. Keep this distinct from
         * UNKNOWN so generated code may deliberately use Nodes for runtime
         * dispatch only when the declared Java type is Object.
         */
        if (isCompileTimeUnknown(type)) {
            return NodeKind.COMPILE_TIME_UNKNOWN;
        }

        return NodeKind.OBJECT_POJO;
    }


    public JsonType jsonType(TypeMirror type) {
        return JsonType.of(nodeKind(type));
    }


    // -------------------------------------------------------------------------
    // Assignability
    // -------------------------------------------------------------------------

    public boolean isAssignable(
            TypeMirror from,
            TypeMirror to) {

        return from != null
                && to != null
                && typeUtils.isAssignable(
                from,
                to);
    }


    public boolean isAssignableErasure(
            TypeMirror from,
            TypeMirror to) {

        return from != null
                && to != null
                && typeUtils.isAssignable(
                typeUtils.erasure(from),
                typeUtils.erasure(to));
    }


    public boolean isSameErasure(
            TypeMirror first,
            TypeMirror second) {

        return first != null
                && second != null
                && typeUtils.isSameType(
                typeUtils.erasure(first),
                typeUtils.erasure(second));
    }


    public boolean isAssignableBoxed(
            TypeMirror from,
            TypeMirror to) {

        return from != null
                && to != null
                && typeUtils.isAssignable(
                typeUtils.erasure(
                        boxed(from)),
                typeUtils.erasure(
                        boxed(to)));
    }


    public boolean isAssignableBoxedGeneric(
            TypeMirror from,
            TypeMirror to) {

        return from != null
                && to != null
                && typeUtils.isAssignable(
                boxed(from),
                boxed(to));
    }


    // -------------------------------------------------------------------------
    // Common type checks
    // -------------------------------------------------------------------------

    public boolean isObject(TypeMirror type) {
        return isSameErasure(type, objectType);
    }


    public boolean isBoolean(TypeMirror type) {
        type = concrete(type);
        return type != null &&
                (type.getKind() == TypeKind.BOOLEAN || isSameErasure(type, booleanType));
    }


    public boolean isNodeValue(TypeMirror type) {
        TypeElement element = typeElement(type);
        return element != null
                && element.getAnnotation(NodeValue.class) != null;
    }

    public boolean isCompileTimeUnknown(TypeMirror type) {
        return isObject(type) || isExternalNode(type);
    }


    /**
     * Returns whether the type belongs to a supported external JSON tree model.
     *
     * <p>This answers capability, not shape. For example JsonNode is an
     * external node even though its precise NodeKind is unknown.</p>
     */
    public boolean isExternalNode(TypeMirror type) {
        return isAssignableErasure(type, jackson3NodeType)
                || isAssignableErasure(type, jackson2NodeType)
                || isAssignableErasure(type, gsonNodeType)
                || isAssignableErasure(type, jsonpNodeType);
    }


    // -------------------------------------------------------------------------
    // Type normalization
    // -------------------------------------------------------------------------

    public TypeElement typeElement(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }

        Element element =
                ((DeclaredType) type)
                        .asElement();

        return element instanceof TypeElement
                ? (TypeElement) element
                : null;
    }


    /**
     * Resolves a wildcard/type-variable to the safest readable concrete type.
     */
    public TypeMirror concrete(TypeMirror type) {
        if (type == null) {
            return null;
        }

        if (type instanceof WildcardType) {
            TypeMirror bound =
                    ((WildcardType) type)
                            .getExtendsBound();

            return bound == null
                    ? objectType
                    : concrete(bound);
        }

        if (type instanceof TypeVariable) {
            TypeMirror bound =
                    ((TypeVariable) type)
                            .getUpperBound();

            if (bound == null ||
                    bound.getKind() ==
                            TypeKind.NULL) {

                return objectType;
            }

            return concrete(bound);
        }

        return type;
    }


    public TypeMirror boxed(TypeMirror type) {
        type = concrete(type);

        if (type != null &&
                type.getKind().isPrimitive()) {

            return typeUtils
                    .boxedClass(
                            (PrimitiveType) type)
                    .asType();
        }

        return type;
    }


    // -------------------------------------------------------------------------
    // Map
    // -------------------------------------------------------------------------

    /**
     * Returns the readable Map key type.
     *
     * <p>Raw Map resolves to Object. {@code ? extends T} resolves to T.
     * Unbounded and lower-bounded wildcards resolve to Object.</p>
     */
    public TypeMirror mapReadKeyType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                mapType)) {

            return null;
        }

        return readableTypeArgument(
                typeArgument(
                        type,
                        mapType,
                        0));
    }


    /**
     * Returns the writable Map key type.
     *
     * <p>Wildcard key types are deliberately treated as non-writable.
     * Raw Map remains writable as Object.</p>
     */
    public TypeMirror mapWriteKeyType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                mapType)) {

            return null;
        }

        return writableTypeArgument(
                typeArgument(
                        type,
                        mapType,
                        0));
    }


    /**
     * Returns whether a String path key can be passed safely to this Map.
     * Raw Maps remain accepted for compatibility. Wildcard and type-variable
     * keys are rejected because generated code cannot establish their write
     * safety.
     */
    public boolean hasSafeStringPathKey(TypeMirror type) {
        TypeMirror key = typeArgument(type, mapType, 0);

        if (key == null) {
            return true;
        }

        if (key instanceof WildcardType ||
                key instanceof TypeVariable) {

            return false;
        }

        return isSameErasure(key, stringType);
    }


    /**
     * Returns the declared key argument for diagnostics, or {@code null} for
     * a raw Map.
     */
    public TypeMirror mapDeclaredKeyType(TypeMirror type) {
        return typeArgument(type, mapType, 0);
    }



    /**
     * Returns the readable Map value type.
     *
     * <pre>
     * Map&lt;String, Foo&gt;           -> Foo
     * Map&lt;String, ? extends Foo&gt; -> Foo
     * Map&lt;String, ?&gt;             -> Object
     * Map&lt;String, ? super Foo&gt;   -> Object
     * raw Map                    -> Object
     * </pre>
     */
    public TypeMirror mapReadValueType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                mapType)) {

            return null;
        }

        return readableTypeArgument(
                typeArgument(
                        type,
                        mapType,
                        1));
    }


    /**
     * Returns the writable Map value type.
     *
     * <p>All wildcard value types are deliberately treated as non-writable.
     * Raw Map remains writable as Object.</p>
     */
    public TypeMirror mapWriteValueType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                mapType)) {

            return null;
        }

        return writableTypeArgument(
                typeArgument(
                        type,
                        mapType,
                        1));
    }


    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------
    /**
     * Returns the readable List element type.
     */
    public TypeMirror listReadElementType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                listType)) {

            return null;
        }

        return readableTypeArgument(
                typeArgument(
                        type,
                        listType,
                        0));
    }


    /**
     * Returns the writable List element type.
     *
     * <p>All wildcard element types are deliberately treated as non-writable.
     * Raw List remains writable as Object.</p>
     */
    public TypeMirror listWriteElementType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                listType)) {

            return null;
        }

        return writableTypeArgument(
                typeArgument(
                        type,
                        listType,
                        0));
    }


    // -------------------------------------------------------------------------
    // Set
    // -------------------------------------------------------------------------

    /**
     * Returns the readable Set element type.
     */
    public TypeMirror setReadElementType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                setType)) {

            return null;
        }

        return readableTypeArgument(
                typeArgument(
                        type,
                        setType,
                        0));
    }


    /**
     * Returns the writable Set element type.
     *
     * <p>All wildcard element types are deliberately treated as non-writable.
     * Raw Set remains writable as Object.</p>
     */
    public TypeMirror setWriteElementType(TypeMirror type) {
        if (!isAssignableErasure(
                type,
                setType)) {

            return null;
        }

        return writableTypeArgument(
                typeArgument(
                        type,
                        setType,
                        0));
    }


    // -------------------------------------------------------------------------
    // Generic array-like type
    // -------------------------------------------------------------------------

    /**
     * Resolves the readable element type of Java arrays, List, Set and dynamic
     * SJF4J/external arrays.
     */
    public TypeMirror readElementType(TypeMirror type) {
        type = concrete(type);

        if (type == null) {
            return null;
        }

        if (type.getKind() ==
                TypeKind.ARRAY) {

            return concrete(
                    ((ArrayType) type)
                            .getComponentType());
        }

        if (isAssignableErasure(
                type,
                listType)) {

            return listReadElementType(type);
        }

        if (isAssignableErasure(
                type,
                setType)) {

            return setReadElementType(type);
        }

        NodeKind kind =
                nodeKind(type);

        if (kind == NodeKind.ARRAY_JSON_ARRAY
                || kind == NodeKind.ARRAY_JAJO
                || kind == NodeKind.ARRAY_EXTERNAL) {

            return objectType;
        }

        return null;
    }


    /**
     * Resolves the writable element type of Java arrays, List, Set and dynamic
     * SJF4J/external arrays.
     *
     * <p>{@code ? extends T} contributes only a readable element type and is
     * therefore rejected here.</p>
     */
    public TypeMirror writeElementType(TypeMirror type) {
        type = concrete(type);

        if (type == null) {
            return null;
        }

        if (type.getKind() ==
                TypeKind.ARRAY) {

            return ((ArrayType) type)
                    .getComponentType();
        }

        if (isAssignableErasure(
                type,
                listType)) {

            return listWriteElementType(type);
        }

        if (isAssignableErasure(
                type,
                setType)) {

            return setWriteElementType(type);
        }

        NodeKind kind =
                nodeKind(type);

        if (kind == NodeKind.ARRAY_JSON_ARRAY
                || kind == NodeKind.ARRAY_JAJO
                || kind == NodeKind.ARRAY_EXTERNAL) {

            return objectType;
        }

        return null;
    }


    public boolean isFullyConcrete(TypeMirror type) {
        if (type == null) {
            return false;
        }

        switch (type.getKind()) {
            case TYPEVAR:
            case WILDCARD:
                return false;

            case ARRAY:
                return isFullyConcrete(
                        ((ArrayType) type)
                                .getComponentType());

            case DECLARED: {
                DeclaredType declared =
                        (DeclaredType) type;

                TypeElement element =
                        (TypeElement)
                                declared.asElement();

                List<? extends TypeMirror> args =
                        declared.getTypeArguments();

                // Raw generic: List / Map / Foo where Foo<T>
                if (!element.getTypeParameters().isEmpty() &&
                        args.isEmpty()) {
                    return false;
                }

                TypeMirror enclosing =
                        declared.getEnclosingType();

                if (enclosing != null &&
                        enclosing.getKind() != TypeKind.NONE &&
                        !isFullyConcrete(enclosing)) {
                    return false;
                }

                for (TypeMirror arg : args) {
                    if (!isFullyConcrete(arg)) {
                        return false;
                    }
                }

                return true;
            }

            case ERROR:
                return false;

            default:
                return true;
        }
    }
    public TypeMirror resolveFieldType(
            TypeMirror owner,
            VariableElement field) {

        DeclaredType declaring =
                declaringType(owner, field);

        if (declaring == null) {
            return null;
        }

        return typeUtils.asMemberOf(
                declaring,
                field);
    }


    public ExecutableType resolveMethodType(
            TypeMirror owner,
            ExecutableElement method) {

        DeclaredType declaring =
                declaringType(owner, method);

        if (declaring == null) {
            return null;
        }

        return (ExecutableType)
                typeUtils.asMemberOf(
                        declaring,
                        method);
    }

    public DeclaredType asSuper(
            TypeMirror type,
            TypeElement target) {

        type = concrete(type);

        if (!(type instanceof DeclaredType)) {
            return null;
        }

        DeclaredType declared =
                (DeclaredType) type;

        if (declared.asElement()
                .equals(target)) {

            return declared;
        }

        for (TypeMirror superType :
                typeUtils.directSupertypes(type)) {

            DeclaredType result =
                    asSuper(
                            superType,
                            target);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Generic argument resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves one generic type argument through inherited generic bindings.
     *
     * <p>For example:</p>
     *
     * <pre>
     * class Names extends ArrayList&lt;String&gt; {}
     * </pre>
     *
     * <p>Resolving Names against List argument 0 returns String.</p>
     *
     * <p>Returns null for a raw target type.</p>
     */
    private TypeMirror typeArgument(
            TypeMirror source,
            TypeMirror target,
            int index) {

        TypeMirror resolved =
                findSuperType(
                        source,
                        target);

        if (!(resolved instanceof DeclaredType)) {
            return null;
        }

        List<? extends TypeMirror> arguments =
                ((DeclaredType) resolved)
                        .getTypeArguments();

        return index < arguments.size()
                ? arguments.get(index)
                : null;
    }


    /**
     * Resolves the source as the requested generic supertype while preserving
     * compiler-substituted type arguments.
     */
    private TypeMirror findSuperType(
            TypeMirror source,
            TypeMirror target) {

        source = concrete(source);

        if (!(source instanceof DeclaredType)) {
            return null;
        }

        if (isSameErasure(
                source,
                target)) {

            return source;
        }

        for (TypeMirror superType :
                typeUtils.directSupertypes(source)) {

            if (!isAssignableErasure(
                    superType,
                    target)) {

                continue;
            }

            TypeMirror resolved =
                    findSuperType(
                            superType,
                            target);

            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }


    /**
     * Resolves a generic argument for reading.
     *
     * <p>Raw types resolve to Object. {@code ? extends T} resolves to T.
     * Unbounded and lower-bounded wildcards resolve to Object.</p>
     */
    private TypeMirror readableTypeArgument(
            TypeMirror argument) {

        /*
         * Raw Map/List/Set.
         */
        if (argument == null) {
            return objectType;
        }

        if (argument instanceof WildcardType) {
            TypeMirror upper =
                    ((WildcardType) argument)
                            .getExtendsBound();

            return upper == null
                    ? objectType
                    : concrete(upper);
        }

        return concrete(argument);
    }


    /**
     * Resolves a generic argument for writing.
     *
     * <p>Wildcard declarations are deliberately rejected, including
     * {@code ?}, {@code ? extends T}, and {@code ? super T}. Raw containers
     * remain writable as Object.</p>
     */
    private TypeMirror writableTypeArgument(
            TypeMirror argument) {

        /*
         * Raw Map/List.
         */
        if (argument == null) {
            return objectType;
        }

        if (argument instanceof WildcardType) {
            return null;
        }

        /*
         * An unresolved type variable is not safe for generated writes.
         */
        if (argument instanceof TypeVariable) {
            return null;
        }

        return concrete(argument);
    }


    // -------------------------------------------------------------------------
    // Common mirrors
    // -------------------------------------------------------------------------

    public TypeMirror objectType() {
        return objectType;
    }

    public TypeMirror mapType() {
        return mapType;
    }

    public TypeMirror listType() {
        return listType;
    }

    public TypeMirror setType() {
        return setType;
    }

    public TypeMirror jsonObjectType() {
        return jsonObjectType;
    }

    public TypeMirror jsonArrayType() {
        return jsonArrayType;
    }


    // -------------------------------------------------------------------------
    // @NodeValue
    // -------------------------------------------------------------------------

    /**
     * Determines the JSON value kind represented by an @NodeValue type from
     * its @ValueToRaw method.
     */
    private NodeKind nodeValueKind(TypeMirror type) {
        TypeMirror rawType =
                nodeValueRawType(type);

        if (rawType == null ||
                isSameErasure(
                        rawType,
                        type)) {

            return NodeKind.UNKNOWN;
        }

        NodeKind kind =
                nodeKind(rawType);

        switch (kind) {
            case VALUE_STRING:
            case VALUE_STRING_CHARACTER:
            case VALUE_STRING_ENUM:
                return NodeKind.VALUE_STRING;

            case VALUE_NUMBER:
                return NodeKind.VALUE_NUMBER;

            case VALUE_BOOLEAN:
                return NodeKind.VALUE_BOOLEAN;

            case VALUE_NULL:
                return NodeKind.VALUE_NULL;

            default:
                return NodeKind.UNKNOWN;
        }
    }


    private TypeMirror nodeValueRawType(
            TypeMirror owner) {

        TypeElement element =
                typeElement(owner);

        if (element == null) {
            return null;
        }

        for (Element member :
                elements.getAllMembers(element)) {

            if (member.getKind() !=
                    ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) member;

            if (method.getAnnotation(
                    ValueToRaw.class) == null) {

                continue;
            }

            if (method.getModifiers()
                    .contains(Modifier.STATIC)
                    || !method.getParameters()
                    .isEmpty()) {

                continue;
            }

            /*
             * Resolve inherited generic return types through the actual owner.
             */
            if (owner instanceof DeclaredType) {
                ExecutableType resolved =
                        (ExecutableType)
                                typeUtils.asMemberOf(
                                        (DeclaredType) owner,
                                        method);

                return resolved.getReturnType();
            }

            return method.getReturnType();
        }

        return null;
    }


    // -------------------------------------------------------------------------
    // Type lookup
    // -------------------------------------------------------------------------

    private TypeMirror requiredType(String name) {
        TypeElement element =
                elements.getTypeElement(name);

        if (element == null) {
            throw new IllegalStateException(
                    "Required type is not available: " +
                            name);
        }

        return element.asType();
    }


    private TypeMirror optionalType(String name) {
        TypeElement element = elements.getTypeElement(name);
        return element == null ? null : element.asType();
    }

    private DeclaredType declaringType(
            TypeMirror owner,
            Element member) {

        Element enclosing =
                member.getEnclosingElement();

        if (!(enclosing instanceof TypeElement)) {
            return null;
        }

        return asSuper(
                owner,
                (TypeElement) enclosing);
    }
}
