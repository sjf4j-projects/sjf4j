package org.sjf4j.processor.property;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.annotation.NodeAnnotations;
import org.sjf4j.processor.type.TypeSystem;

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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves the node-facing readable and writable properties of Java object types.
 */
public final class PropertyResolver {

    private static final String JSON_OBJECT =
            "org.sjf4j.JsonObject";
    private static final String JSON_CONTAINER =
            "org.sjf4j.JsonContainer";
    private static final String JAVA_OBJECT =
            "java.lang.Object";

    private final Types typeUtils;
    private final Elements elements;
    private final TypeSystem types;
    private final NodeAnnotations annotations;

    public PropertyResolver(
            Types typeUtils,
            Elements elements,
            TypeSystem types,
            NodeAnnotations annotations) {

        this.typeUtils = Objects.requireNonNull(typeUtils, "typeUtils");
        this.elements = Objects.requireNonNull(elements, "elements");
        this.types = Objects.requireNonNull(types, "types");
        this.annotations = Objects.requireNonNull(annotations, "annotations");
    }

    /**
     * Resolves all node-facing properties of a POJO or JOJO.
     */
    public Map<String, Property> resolve(TypeMirror owner) {
        owner = types.concrete(owner);

        if (!(owner instanceof DeclaredType)) {
            return Collections.emptyMap();
        }

        NodeKind kind = types.nodeKind(owner);

        if (kind != NodeKind.OBJECT_POJO
                && kind != NodeKind.OBJECT_JOJO) {
            return Collections.emptyMap();
        }

        TypeElement type = types.typeElement(owner);

        if (type == null) {
            return Collections.emptyMap();
        }

        DeclaredType declaredOwner = (DeclaredType) owner;
        boolean jojo = kind == NodeKind.OBJECT_JOJO;

        Map<String, Builder> builders =
                new LinkedHashMap<String, Builder>();

        for (Element member : elements.getAllMembers(type)) {
            if (jojo && isJsonBaseMember(member)) {
                continue;
            }

            Set<Modifier> modifiers = member.getModifiers();

            if (!modifiers.contains(Modifier.PUBLIC)
                    || modifiers.contains(Modifier.STATIC)) {
                continue;
            }

            if (member.getKind() == ElementKind.FIELD) {
                addField(
                        builders,
                        declaredOwner,
                        (VariableElement) member);
            } else if (member.getKind() == ElementKind.METHOD) {
                addMethod(
                        builders,
                        type,
                        declaredOwner,
                        (ExecutableElement) member);
            }
        }

        Map<String, Property> result =
                new LinkedHashMap<String, Property>(builders.size());

        for (Map.Entry<String, Builder> entry
                : builders.entrySet()) {

            Builder builder = entry.getValue();

            result.put(
                    entry.getKey(),
                    new Property(
                            entry.getKey(),
                            builder.read == null
                                    ? null
                                    : builder.read.access,
                            builder.write == null
                                    ? null
                                    : builder.write.access));
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Resolves one node-facing property.
     */
    public Property resolve(TypeMirror owner, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        return resolve(owner).get(name);
    }

    private void addField(
            Map<String, Builder> builders,
            DeclaredType owner,
            VariableElement field) {

        String javaName =
                field.getSimpleName().toString();

        String explicitName =
                annotations.explicitPropertyName(field);

        String propertyName =
                explicitName == null
                        ? javaName
                        : explicitName;

        int priority =
                priority(explicitName != null, false);

        TypeMirror fieldType =
                typeUtils.asMemberOf(owner, field);

        PropertyAccess access =
                new PropertyAccess(field, fieldType);

        builder(builders, propertyName)
                .offerRead(access, priority);

        if (!field.getModifiers().contains(Modifier.FINAL)) {
            builder(builders, propertyName)
                    .offerWrite(access, priority);
        }
    }

    private void addMethod(
            Map<String, Builder> builders,
            TypeElement type,
            DeclaredType owner,
            ExecutableElement method) {

        ExecutableType resolved =
                (ExecutableType) typeUtils.asMemberOf(
                        owner,
                        method);

        String explicitName =
                annotations.explicitPropertyName(method);

        /*
         * Read:
         *
         *   getX()
         *   isX()
         *   record x()
         *   @NodeProperty arbitraryMethod()
         */
        if (method.getParameters().isEmpty()
                && resolved.getReturnType().getKind()
                != TypeKind.VOID) {

            String baseName =
                    readablePropertyName(
                            type,
                            method,
                            resolved.getReturnType());

            if (explicitName != null || baseName != null) {
                String propertyName =
                        explicitName == null
                                ? baseName
                                : explicitName;

                int priority =
                        priority(
                                explicitName != null,
                                true);

                builder(builders, propertyName)
                        .offerRead(
                                new PropertyAccess(
                                        method,
                                        resolved.getReturnType()),
                                priority);
            }
        }

        /*
         * Write:
         *
         *   setX(value)
         *
         * An annotation may rename a setter, but does not turn an arbitrary
         * one-argument method into a setter.
         */
        if (method.getParameters().size() == 1
                && resolved.getReturnType().getKind()
                == TypeKind.VOID) {

            String baseName =
                    writablePropertyName(method);

            if (baseName != null) {
                String propertyName =
                        explicitName == null
                                ? baseName
                                : explicitName;

                int priority =
                        priority(
                                explicitName != null,
                                true);

                builder(builders, propertyName)
                        .offerWrite(
                                new PropertyAccess(
                                        method,
                                        resolved
                                                .getParameterTypes()
                                                .get(0)),
                                priority);
            }
        }
    }

    private String readablePropertyName(
            TypeElement type,
            ExecutableElement method,
            TypeMirror returnType) {

        String name =
                method.getSimpleName().toString();

        if (name.equals("getClass")) {
            return null;
        }

        if (isRecordAccessor(type, name)) {
            return name;
        }

        if (name.startsWith("get")
                && name.length() > 3) {
            return decap(name.substring(3));
        }

        if (name.startsWith("is")
                && name.length() > 2
                && types.isBoolean(returnType)) {

            return decap(name.substring(2));
        }

        return null;
    }

    private String writablePropertyName(
            ExecutableElement method) {

        String name =
                method.getSimpleName().toString();

        if (name.startsWith("set")
                && name.length() > 3) {
            return decap(name.substring(3));
        }

        return null;
    }

    private boolean isRecordAccessor(
            TypeElement type,
            String methodName) {

        if (!"RECORD".equals(type.getKind().name())) {
            return false;
        }

        for (Element element : type.getEnclosedElements()) {
            if ("RECORD_COMPONENT".equals(
                    element.getKind().name())
                    && element
                    .getSimpleName()
                    .contentEquals(methodName)) {

                return true;
            }
        }

        return false;
    }

    private boolean isJsonBaseMember(Element member) {
        Element owner = member.getEnclosingElement();

        if (!(owner instanceof TypeElement)) {
            return false;
        }

        String name =
                ((TypeElement) owner)
                        .getQualifiedName()
                        .toString();

        return name.equals(JSON_OBJECT)
                || name.equals(JSON_CONTAINER)
                || name.equals(JAVA_OBJECT);
    }

    /**
     * Resolution precedence:
     *
     * explicit method > explicit field > method > field
     */
    private int priority(
            boolean explicit,
            boolean method) {

        return (explicit ? 2 : 0)
                + (method ? 1 : 0);
    }

    private Builder builder(
            Map<String, Builder> builders,
            String name) {

        Builder builder = builders.get(name);

        if (builder == null) {
            builder = new Builder();
            builders.put(name, builder);
        }

        return builder;
    }

    private String decap(String value) {
        if (value.length() < 2 ||
                !Character.isUpperCase(value.charAt(0)) ||
                !Character.isUpperCase(value.charAt(1))) {

            return value.isEmpty()
                    ? value
                    : Character.toLowerCase(value.charAt(0))
                    + value.substring(1);
        }

        return value;
    }

    private static final class Candidate {

        final PropertyAccess access;
        final int priority;

        Candidate(
                PropertyAccess access,
                int priority) {

            this.access = access;
            this.priority = priority;
        }
    }

    private static final class Builder {

        Candidate read;
        Candidate write;

        void offerRead(
                PropertyAccess access,
                int priority) {

            if (read == null
                    || priority > read.priority) {
                read = new Candidate(access, priority);
            }
        }

        void offerWrite(
                PropertyAccess access,
                int priority) {

            if (write == null
                    || priority > write.priority) {
                write = new Candidate(access, priority);
            }
        }
    }
}
