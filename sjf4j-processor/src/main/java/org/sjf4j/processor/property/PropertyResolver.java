package org.sjf4j.processor.property;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.annotation.NodeAnnotations;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.HashMap;
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
    private final Messager messager;
    private final TypeSystem types;
    private final NodeAnnotations annotations;

    private final Map<String, Map<String, Property>> cache =
            new HashMap<String, Map<String, Property>>();

    public PropertyResolver(
            Types typeUtils,
            Elements elements,
            Messager messager,
            TypeSystem types,
            NodeAnnotations annotations) {

        this.typeUtils = Objects.requireNonNull(typeUtils, "typeUtils");
        this.elements = Objects.requireNonNull(elements, "elements");
        this.messager = Objects.requireNonNull(messager, "messager");
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

        String cacheKey = owner.toString();

        Map<String, Property> cached =
                cache.get(cacheKey);

        if (cached != null) {
            return cached;
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
                        type,
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

        Map<String, Property> resolved =
                Collections.unmodifiableMap(result);

        cache.put(
                cacheKey,
                resolved);

        return resolved;
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
            TypeElement type,
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

        builder(builders, propertyName, type)
                .offerRead(access, priority);

        if (!field.getModifiers().contains(Modifier.FINAL)) {
            builder(builders, propertyName, type)
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

                builder(builders, propertyName, type)
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

                builder(builders, propertyName, type)
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
            String name,
            TypeElement owner) {

        Builder builder = builders.get(name);

        if (builder == null) {
            builder = new Builder(owner, name);
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


    private final class Builder {

        private final TypeElement owner;
        private final String name;

        Candidate read;
        Candidate write;


        Builder(
                TypeElement owner,
                String name) {

            this.owner = owner;
            this.name = name;
        }


        void offerRead(
                PropertyAccess access,
                int priority) {

            read = offer(
                    read,
                    access,
                    priority,
                    true);
        }


        void offerWrite(
                PropertyAccess access,
                int priority) {

            write = offer(
                    write,
                    access,
                    priority,
                    false);
        }


        private Candidate offer(
                Candidate current,
                PropertyAccess candidate,
                int priority,
                boolean readable) {

            if (current == null ||
                    priority > current.priority) {

                return new Candidate(
                        candidate,
                        priority);
            }

            if (priority < current.priority) {
                return current;
            }

            int specificity =
                    compareSpecificity(
                            owner,
                            current.access,
                            candidate,
                            readable);

            if (specificity < 0) {
                return current;
            }

            if (specificity > 0) {
                return new Candidate(
                        candidate,
                        priority);
            }

            reportAmbiguous(
                    name,
                    readable,
                    current.access,
                    candidate);

            return candidateKey(candidate)
                    .compareTo(
                            candidateKey(
                                    current.access)) < 0
                    ? new Candidate(
                    candidate,
                    priority)
                    : current;
        }
    }


    /**
     * Returns -1 for current, +1 for candidate and 0 for a real ambiguity.
     */
    private int compareSpecificity(
            TypeElement owner,
            PropertyAccess current,
            PropertyAccess candidate,
            boolean readable) {

        if (current.member().equals(
                candidate.member())) {
            return -1;
        }

        if (current.isMethod() &&
                candidate.isMethod()) {

            ExecutableElement currentMethod =
                    (ExecutableElement) current.member();

            ExecutableElement candidateMethod =
                    (ExecutableElement) candidate.member();

            try {
                if (elements.overrides(
                        candidateMethod,
                        currentMethod,
                        owner)) {
                    return 1;
                }

                if (elements.overrides(
                        currentMethod,
                        candidateMethod,
                        owner)) {
                    return -1;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to signature/type comparison.
            }

            if (readable) {
                int booleanGetter =
                        compareBooleanGetter(
                                currentMethod,
                                candidateMethod);

                if (booleanGetter != 0) {
                    return booleanGetter;
                }
            }

            if (sameMethodSignature(
                    current,
                    candidate)) {

                if (readable) {
                    boolean candidateToCurrent =
                            typeUtils.isAssignable(
                                    candidate.type(),
                                    current.type());

                    boolean currentToCandidate =
                            typeUtils.isAssignable(
                                    current.type(),
                                    candidate.type());

                    if (candidateToCurrent &&
                            !currentToCandidate) {
                        return 1;
                    }

                    if (currentToCandidate &&
                            !candidateToCurrent) {
                        return -1;
                    }
                }

                /*
                 * Equivalent diamond declarations represent one Java member
                 * contract and are not an ambiguous node property.
                 */
                return candidateKey(candidate)
                        .compareTo(
                                candidateKey(current)) < 0
                        ? 1
                        : -1;
            }

            return 0;
        }

        if (current.isField() &&
                candidate.isField()) {

            TypeElement currentOwner =
                    memberOwner(current.member());

            TypeElement candidateOwner =
                    memberOwner(candidate.member());

            if (currentOwner != null &&
                    candidateOwner != null) {

                boolean candidateToCurrent =
                        typeUtils.isSubtype(
                                candidateOwner.asType(),
                                currentOwner.asType());

                boolean currentToCandidate =
                        typeUtils.isSubtype(
                                currentOwner.asType(),
                                candidateOwner.asType());

                if (candidateToCurrent &&
                        !currentToCandidate) {
                    return 1;
                }

                if (currentToCandidate &&
                        !candidateToCurrent) {
                    return -1;
                }
            }
        }

        return 0;
    }


    private int compareBooleanGetter(
            ExecutableElement current,
            ExecutableElement candidate) {

        String currentName =
                current.getSimpleName()
                        .toString();

        String candidateName =
                candidate.getSimpleName()
                        .toString();

        boolean currentIs =
                isBooleanGetterName(currentName);

        boolean candidateIs =
                isBooleanGetterName(candidateName);

        if (currentIs == candidateIs) {
            return 0;
        }

        String isName =
                currentIs
                        ? currentName
                        : candidateName;

        String otherName =
                currentIs
                        ? candidateName
                        : currentName;

        if (!otherName.startsWith("get") ||
                otherName.length() <= 3 ||
                !isName.substring(2)
                        .equals(otherName.substring(3))) {
            return 0;
        }

        return candidateIs ? 1 : -1;
    }


    private boolean isBooleanGetterName(
            String name) {

        return name.startsWith("is") &&
                name.length() > 2;
    }


    private boolean sameMethodSignature(
            PropertyAccess first,
            PropertyAccess second) {

        ExecutableElement firstMethod =
                (ExecutableElement) first.member();

        ExecutableElement secondMethod =
                (ExecutableElement) second.member();

        if (!firstMethod.getSimpleName()
                .contentEquals(
                        secondMethod.getSimpleName())) {
            return false;
        }

        int parameters =
                firstMethod.getParameters().size();

        if (parameters !=
                secondMethod.getParameters().size()) {
            return false;
        }

        if (parameters == 0) {
            return true;
        }

        if (parameters == 1) {
            return typeUtils.isSameType(
                    typeUtils.erasure(first.type()),
                    typeUtils.erasure(second.type()));
        }

        return false;
    }


    private TypeElement memberOwner(
            Element member) {

        Element owner =
                member.getEnclosingElement();

        return owner instanceof TypeElement
                ? (TypeElement) owner
                : null;
    }


    private void reportAmbiguous(
            String name,
            boolean readable,
            PropertyAccess first,
            PropertyAccess second) {

        messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Ambiguous " +
                        (readable ? "readable" : "writable") +
                        " node property '" +
                        name +
                        "': " +
                        memberDescription(first) +
                        " and " +
                        memberDescription(second),
                second.member());
    }


    private String memberDescription(
            PropertyAccess access) {

        TypeElement owner =
                memberOwner(access.member());

        return (owner == null
                ? ""
                : owner.getQualifiedName() + "#") +
                access.member().toString();
    }


    private String candidateKey(
            PropertyAccess access) {

        return memberDescription(access) +
                ':' +
                access.type();
    }

}
