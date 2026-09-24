package org.sjf4j.processor.binding;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.property.PropertyAccess;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles Java types into recursive direct-binding semantics before source
 * emission.
 *
 * <p>V2 deliberately permits runtime dispatch only for
 * {@link NodeKind#COMPILE_TIME_UNKNOWN}. A statically known shape that is not
 * supported by the direct compiler is rejected instead of silently falling
 * back to runtime binding.</p>
 */
final class BindingCompiler {

    private final ProcessorContext context;
    private final NameAllocator names;

    private final TypeMirror stringType;
    private final TypeMirror arrayListType;
    private final TypeMirror linkedHashSetType;
    private final TypeMirror linkedHashMapType;

    private final Map<String, BindingValue> cache =
            new LinkedHashMap<String, BindingValue>();

    private final List<BindingValue> helpers =
            new ArrayList<BindingValue>();

    BindingCompiler(
            ProcessorContext context,
            NameAllocator names) {

        this.context = context;
        this.names = names;

        this.stringType = requiredType("java.lang.String");
        this.arrayListType = requiredType("java.util.ArrayList");
        this.linkedHashSetType = requiredType("java.util.LinkedHashSet");
        this.linkedHashMapType = requiredType("java.util.LinkedHashMap");
    }

    CompiledMethod compile(
            BindingPlan plan,
            GeneratedClass generated) {

        BindingValue value =
                compileValue(
                        plan.direction(),
                        plan.valueType(),
                        plan.method().declaration(),
                        generated);

        return value == null
                ? null
                : new CompiledMethod(plan, value);
    }

    Collection<BindingValue> helpers() {
        return Collections.unmodifiableList(helpers);
    }

    private BindingValue compileValue(
            BindingPlan.Direction direction,
            TypeMirror inputType,
            ExecutableElement method,
            GeneratedClass generated) {

        if (inputType == null) {
            error(
                    method,
                    generated,
                    "Cannot resolve binding type");
            return null;
        }

        TypeMirror type =
                context.types.concrete(inputType);

        String key =
                direction.name() + ':' + type.toString();

        BindingValue existing =
                cache.get(key);

        if (existing != null) {
            return existing;
        }

        BindingValue.Kind kind =
                resolveKind(
                        direction,
                        type,
                        method,
                        generated);

        if (kind == null) {
            return null;
        }

        BindingValue value =
                new BindingValue(
                        direction,
                        type,
                        kind,
                        type.getKind().isPrimitive());

        /*
         * Cache the placeholder before recursively compiling children so
         * recursive POJO/container graphs terminate correctly.
         */
        cache.put(key, value);

        if (value.helperRequired()) {
            value.helperName(
                    names.newName(
                            helperSuggestion(
                                    direction,
                                    type)));

            helpers.add(value);
        }

        switch (kind) {
            case POJO:
                if (!compilePojo(
                        value,
                        method,
                        generated)) {
                    return null;
                }
                break;

            case LIST:
            case SET:
                if (!compileElement(
                        value,
                        method,
                        generated)) {
                    return null;
                }
                break;

            case MAP:
                if (!compileMap(
                        value,
                        method,
                        generated)) {
                    return null;
                }
                break;

            default:
                break;
        }

        return value;
    }

    private BindingValue.Kind resolveKind(
            BindingPlan.Direction direction,
            TypeMirror type,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeKind javaKind =
                type.getKind();

        if (javaKind.isPrimitive()) {
            switch (javaKind) {
                case BOOLEAN:
                    return BindingValue.Kind.BOOLEAN;
                case BYTE:
                    return BindingValue.Kind.BYTE;
                case SHORT:
                    return BindingValue.Kind.SHORT;
                case INT:
                    return BindingValue.Kind.INT;
                case LONG:
                    return BindingValue.Kind.LONG;
                case FLOAT:
                    return BindingValue.Kind.FLOAT;
                case DOUBLE:
                    return BindingValue.Kind.DOUBLE;
                case CHAR:
                    return BindingValue.Kind.CHARACTER;
                default:
                    return unsupported(
                            method,
                            generated,
                            type,
                            "primitive kind " + javaKind);
            }
        }

        String erased =
                context.typeUtils
                        .erasure(type)
                        .toString();

        if ("java.lang.String".equals(erased)) {
            return BindingValue.Kind.STRING;
        }
        if ("java.lang.Character".equals(erased)) {
            return BindingValue.Kind.CHARACTER;
        }
        if ("java.lang.Boolean".equals(erased)) {
            return BindingValue.Kind.BOOLEAN;
        }
        if ("java.lang.Byte".equals(erased)) {
            return BindingValue.Kind.BYTE;
        }
        if ("java.lang.Short".equals(erased)) {
            return BindingValue.Kind.SHORT;
        }
        if ("java.lang.Integer".equals(erased)) {
            return BindingValue.Kind.INT;
        }
        if ("java.lang.Long".equals(erased)) {
            return BindingValue.Kind.LONG;
        }
        if ("java.lang.Float".equals(erased)) {
            return BindingValue.Kind.FLOAT;
        }
        if ("java.lang.Double".equals(erased)) {
            return BindingValue.Kind.DOUBLE;
        }
        if ("java.lang.Number".equals(erased)) {
            return BindingValue.Kind.NUMBER;
        }
        if ("java.math.BigInteger".equals(erased)) {
            return BindingValue.Kind.BIG_INTEGER;
        }
        if ("java.math.BigDecimal".equals(erased)) {
            return BindingValue.Kind.BIG_DECIMAL;
        }

        TypeElement element =
                context.types.typeElement(type);

        if (element != null &&
                element.getKind() == ElementKind.ENUM) {
            return BindingValue.Kind.ENUM;
        }

        NodeKind nodeKind =
                context.types.nodeKind(type);

        switch (nodeKind) {
            case COMPILE_TIME_UNKNOWN:
                return BindingValue.Kind.RUNTIME;

            case OBJECT_POJO:
                if (direction ==
                        BindingPlan.Direction.WRITE_TO) {
                    return BindingValue.Kind.POJO;
                }

                if (hasPublicNoArgsConstructor(type)) {
                    return BindingValue.Kind.POJO;
                }

                return unsupported(
                        method,
                        generated,
                        type,
                        "read binding currently requires a public no-args constructor; creator support is deferred");

            case ARRAY_LIST:
                if (directList(direction, type)) {
                    return BindingValue.Kind.LIST;
                }
                return unsupported(
                        method,
                        generated,
                        type,
                        "List type cannot be bound directly with the declared generic/write constraints");

            case ARRAY_SET:
                if (directSet(direction, type)) {
                    return BindingValue.Kind.SET;
                }
                return unsupported(
                        method,
                        generated,
                        type,
                        "Set type cannot be bound directly with the declared generic/write constraints");

            case OBJECT_MAP:
                if (directMap(direction, type)) {
                    return BindingValue.Kind.MAP;
                }
                return unsupported(
                        method,
                        generated,
                        type,
                        "Map direct binding requires a String key and a directly writable/readable value type");

            case UNKNOWN:
                return unsupported(
                        method,
                        generated,
                        type,
                        "compile-time node kind is UNKNOWN");

            default:
                return unsupported(
                        method,
                        generated,
                        type,
                        "node kind " + nodeKind +
                                " is not yet supported by V2 direct binding");
        }
    }

    private boolean compilePojo(
            BindingValue value,
            ExecutableElement method,
            GeneratedClass generated) {

        Map<String, Property> properties =
                context.properties.resolve(
                        value.type());

        List<BindingProperty> compiled =
                new ArrayList<BindingProperty>(
                        properties.size());

        for (Property property :
                properties.values()) {

            PropertyAccess access =
                    value.direction() ==
                            BindingPlan.Direction.READ_FROM
                            ? property.write()
                            : property.read();

            if (access == null) {
                continue;
            }

            BindingValue propertyValue =
                    compileValue(
                            value.direction(),
                            access.type(),
                            method,
                            generated);

            if (propertyValue == null) {
                return false;
            }

            compiled.add(
                    new BindingProperty(
                            property.name(),
                            access,
                            propertyValue));
        }

        value.properties(compiled);
        return true;
    }

    private boolean compileElement(
            BindingValue value,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeMirror elementType;

        if (value.kind() == BindingValue.Kind.LIST) {
            elementType =
                    value.direction() ==
                            BindingPlan.Direction.READ_FROM
                            ? context.types.listWriteElementType(
                                    value.type())
                            : context.types.listReadElementType(
                                    value.type());
        } else {
            elementType =
                    value.direction() ==
                            BindingPlan.Direction.READ_FROM
                            ? context.types.setWriteElementType(
                                    value.type())
                            : context.types.setReadElementType(
                                    value.type());
        }

        BindingValue elementValue =
                compileValue(
                        value.direction(),
                        elementType,
                        method,
                        generated);

        if (elementValue == null) {
            return false;
        }

        value.elementValue(elementValue);
        return true;
    }

    private boolean compileMap(
            BindingValue value,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeMirror mapValueType =
                value.direction() ==
                        BindingPlan.Direction.READ_FROM
                        ? context.types.mapWriteValueType(
                                value.type())
                        : context.types.mapReadValueType(
                                value.type());

        BindingValue mapValue =
                compileValue(
                        value.direction(),
                        mapValueType,
                        method,
                        generated);

        if (mapValue == null) {
            return false;
        }

        value.mapValue(mapValue);
        return true;
    }

    private boolean directList(
            BindingPlan.Direction direction,
            TypeMirror type) {

        if (direction == BindingPlan.Direction.READ_FROM) {
            TypeMirror elementType =
                    context.types.listWriteElementType(type);

            return elementType != null &&
                    canAssignImplementation(
                            arrayListType,
                            type);
        }

        return context.types.listReadElementType(type) != null;
    }

    private boolean directSet(
            BindingPlan.Direction direction,
            TypeMirror type) {

        if (direction == BindingPlan.Direction.READ_FROM) {
            TypeMirror elementType =
                    context.types.setWriteElementType(type);

            return elementType != null &&
                    canAssignImplementation(
                            linkedHashSetType,
                            type);
        }

        return context.types.setReadElementType(type) != null;
    }

    private boolean directMap(
            BindingPlan.Direction direction,
            TypeMirror type) {

        if (direction == BindingPlan.Direction.READ_FROM) {
            TypeMirror keyType =
                    context.types.mapWriteKeyType(type);

            TypeMirror valueType =
                    context.types.mapWriteValueType(type);

            return keyType != null &&
                    valueType != null &&
                    context.typeUtils.isAssignable(
                            stringType,
                            keyType) &&
                    canAssignImplementation(
                            linkedHashMapType,
                            type);
        }

        TypeMirror keyType =
                context.types.mapReadKeyType(type);

        TypeMirror valueType =
                context.types.mapReadValueType(type);

        return keyType != null &&
                valueType != null &&
                context.types.isSameErasure(
                        keyType,
                        stringType);
    }

    private boolean canAssignImplementation(
            TypeMirror implementation,
            TypeMirror target) {

        return context.typeUtils.isAssignable(
                context.typeUtils.erasure(
                        implementation),
                context.typeUtils.erasure(
                        target));
    }

    private boolean hasPublicNoArgsConstructor(
            TypeMirror type) {

        TypeElement element =
                context.types.typeElement(type);

        if (element == null ||
                element.getKind() == ElementKind.INTERFACE ||
                "RECORD".equals(
                        element.getKind().name()) ||
                element.getModifiers().contains(
                        Modifier.ABSTRACT) ||
                !element.getModifiers().contains(
                        Modifier.PUBLIC)) {
            return false;
        }

        if (element.getNestingKind() ==
                NestingKind.MEMBER &&
                !element.getModifiers().contains(
                        Modifier.STATIC)) {
            return false;
        }

        boolean declaredConstructor = false;

        for (Element enclosed :
                element.getEnclosedElements()) {

            if (enclosed.getKind() !=
                    ElementKind.CONSTRUCTOR) {
                continue;
            }

            declaredConstructor = true;

            ExecutableElement constructor =
                    (ExecutableElement) enclosed;

            if (constructor.getParameters().isEmpty() &&
                    constructor.getModifiers().contains(
                            Modifier.PUBLIC)) {
                return true;
            }
        }

        return !declaredConstructor;
    }

    private BindingValue.Kind unsupported(
            ExecutableElement method,
            GeneratedClass generated,
            TypeMirror type,
            String reason) {

        error(
                method,
                generated,
                "Cannot generate direct " +
                        "binding for " + type +
                        ": " + reason);

        return null;
    }

    private void error(
            ExecutableElement method,
            GeneratedClass generated,
            String message) {

        context.error(method, message);
        generated.invalidate();
    }

    private String helperSuggestion(
            BindingPlan.Direction direction,
            TypeMirror type) {

        TypeElement element =
                context.types.typeElement(type);

        String simple =
                element == null
                        ? "Value"
                        : element.getSimpleName()
                                .toString();

        return direction ==
                BindingPlan.Direction.READ_FROM
                ? "_read" + simple
                : "_write" + simple;
    }

    private TypeMirror requiredType(String name) {
        TypeElement element =
                context.elements.getTypeElement(name);

        if (element == null) {
            throw new IllegalStateException(
                    "Required type is not available: " +
                            name);
        }

        return element.asType();
    }

    static final class CompiledMethod {

        private final BindingPlan plan;
        private final BindingValue value;

        CompiledMethod(
                BindingPlan plan,
                BindingValue value) {

            this.plan = plan;
            this.value = value;
        }

        BindingPlan plan() {
            return plan;
        }

        BindingValue value() {
            return value;
        }
    }
}
