package org.sjf4j.processor.path;

import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.access.ResolvedStep;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.type.TypeSystem;

import org.sjf4j.processor.path.PathPlan.Step;
import org.sjf4j.processor.path.PathPlan.Creation;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compiles analyzed Get/Put paths into immutable plans, without emitting Java. */
final class PathCompiler {

    private final CompilationContext context;
    private final TypeSystem types;

    private final TypeMirror stringType;
    private final TypeMirror mapType;
    private final TypeMirror listType;
    private final TypeMirror jsonObjectType;
    private final TypeMirror jsonArrayType;

    PathCompiler(CompilationContext context) {
        this.context = context;
        this.types = context.types;

        this.stringType = type(String.class.getName());
        this.mapType = type("java.util.Map");
        this.listType = type("java.util.List");
        this.jsonObjectType = type("org.sjf4j.JsonObject");
        this.jsonArrayType = type("org.sjf4j.JsonArray");
    }

    /*
     * --------------------------------------------------------------
     * Compile
     * --------------------------------------------------------------
     */

    PathPlan.Get compileGet(
            NavigatorPlan.Get plan,
            GeneratedClass generated) {

        TypeMirror currentType = plan.root.asType();
        List<Step> steps = new ArrayList<>();
        PathSegment[] segments = plan.path.segments();

        for (int i = 1; i < segments.length; i++) {
            Step step = resolveStep(currentType, segments[i], plan.pathParameters,
                    plan.method, generated, "@GetByPath", false);

            if (step == null) return null;

            if (step.resolved.access() == null ||
                    !step.resolved.access().readable()) {

                accessError(
                        plan.method,
                        generated,
                        currentType,
                        step,
                        true);

                return null;
            }

            steps.add(step);

            currentType = step.resultType;
        }

        if (!types.isAssignableBoxedGeneric(
                currentType,
                plan.method.getReturnType())) {

            error(
                    plan.method,
                    generated,
                    "@GetByPath return type mismatch: cannot assign " +
                            currentType +
                            " to " +
                            plan.method.getReturnType());

            return null;
        }

        return new PathPlan.Get(plan, steps);
    }

    /*
     * --------------------------------------------------------------
     * Compile Put
     * --------------------------------------------------------------
     */

    PathPlan.Put compilePut(
            NavigatorPlan.Put plan,
            GeneratedClass generated) {

        TypeMirror currentType = plan.root.asType();
        PathSegment[] segments = plan.path.segments();
        List<Step> steps = new ArrayList<>();

        /*
         * Phase 1:
         * resolve the complete intermediate path.
         */
        for (int i = 1; i < segments.length - 1; i++) {
            PathSegment segment = segments[i];
            Step step = resolveStep(currentType, segment, plan.pathParameters,
                    plan.method, generated, plan.annotation, segment instanceof PathSegment.Append);

            if (step == null) return null;

            if (segment instanceof PathSegment.Append) {
                if (!plan.ensure) {
                    error(
                            plan.method,
                            generated,
                            plan.annotation +
                                    " append segment must be the final path segment");
                    return null;
                }

                if (step.resolved.access() == null ||
                        !step.resolved.access().writable()) {

                    accessError(
                            plan.method,
                            generated,
                            currentType,
                            step,
                            false);

                    return null;
                }

            } else {
                if (step.resolved.access() == null ||
                        !step.resolved.access().readable()) {

                    accessError(
                            plan.method,
                            generated,
                            currentType,
                            step,
                            true);

                    return null;
                }

                if (plan.ensure &&
                        (step.resolved.access().kind() == NodeAccess.Kind.LIST ||
                                step.resolved.access().kind() == NodeAccess.Kind.MAP) &&
                        step.resolved.access().writeType() == null) {

                    error(
                            plan.method,
                            generated,
                            plan.annotation +
                                    " cannot materialize through wildcard container " +
                                    step.resolved.ownerType());

                    return null;
                }

            }

            steps.add(step);

            currentType = step.resultType;
        }

        /*
         * Resolve final write target.
         */
        Step target = resolveStep(currentType, segments[segments.length - 1],
                plan.pathParameters, plan.method, generated, plan.annotation, true);

        if (target == null) return null;

        if (target.resolved.access() == null ||
                !target.resolved.access().writable()) {

            accessError(
                    plan.method,
                    generated,
                    currentType,
                    target,
                    false);

            return null;
        }

        if (plan.ifAbsent &&
                !(target.resolved.segment() instanceof PathSegment.Append)) {

            if (!target.resolved.access().readable()) {
                accessError(
                        plan.method,
                        generated,
                        currentType,
                        target,
                        true);

                return null;
            }

            /*
             * Primitive values cannot represent absence.
             */
            if (target.resolved.access()
                    .readType()
                    .getKind()
                    .isPrimitive()) {

                error(
                        plan.method,
                        generated,
                        plan.annotation +
                                " cannot target primitive value " +
                                target.resolved.access().readType());

                return null;
            }
        }

        TypeMirror valueType = target.resolved.access().writeType();

        if (!types.isAssignableBoxedGeneric(
                plan.value.asType(),
                valueType)) {

            error(
                    plan.method,
                    generated,
                    plan.annotation +
                            " value type mismatch: cannot assign " +
                            plan.value.asType() +
                            " to " +
                            valueType);

            return null;
        }

        TypeMirror oldType = target.oldType;

        if (!validatePutReturn(
                plan,
                target,
                oldType,
                generated)) {

            return null;
        }

        /*
         * Phase 2:
         * now that the whole path is structurally valid, prepare allocation
         * expressions needed by ensure traversal.
         */
        if (plan.ensure &&
                !prepareEnsureCreations(
                        plan,
                        steps,
                        target,
                        generated)) {

            return null;
        }

        return new PathPlan.Put(plan, steps, target);
    }

    private boolean validatePutReturn(
            NavigatorPlan.Put plan,
            Step target,
            TypeMirror oldType,
            GeneratedClass generated) {

        TypeMirror returnType = plan.method.getReturnType();

        if (returnType.getKind() == TypeKind.VOID) return true;

        /*
         * Append has no old value.
         */
        if (oldType == null) {
            if (returnType.getKind().isPrimitive()) {
                error(
                        plan.method,
                        generated,
                        plan.annotation +
                                " return type mismatch: append returns null");

                return false;
            }

            return true;
        }

        if (!types.isAssignableBoxedGeneric(
                oldType,
                returnType)) {

            error(
                    plan.method,
                    generated,
                    plan.annotation +
                            " return type mismatch: cannot assign " +
                            oldType +
                            " to " +
                            returnType);

            return false;
        }

        /*
         * A primitive method return cannot represent any nullable result.
         *
         * Reference-valued properties/containers may return null as the old
         * value. Conditional put variants may also return null without writing.
         */
        if (returnType.getKind().isPrimitive()) {
            boolean nullable = plan.ifParentPresent || plan.ifAbsent || !oldType.getKind().isPrimitive();

            if (nullable) {
                error(
                        plan.method,
                        generated,
                        plan.annotation +
                                " primitive return type cannot represent a null old value");

                return false;
            }
        }

        return true;
    }

    /*
     * --------------------------------------------------------------
     * Ensure
     * --------------------------------------------------------------
     */

    private boolean prepareEnsureCreations(
            NavigatorPlan.Put plan,
            List<Step> steps,
            Step target,
            GeneratedClass generated) {

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);

            PathSegment next =
                    i + 1 < steps.size()
                            ? steps.get(i + 1).resolved.segment()
                            : target.resolved.segment();

            if (step.resolved.segment() instanceof PathSegment.Append) {
                Creation creation = resolveContainerCreation(step.resultType, next,
                        plan.pathParameters, plan.method, generated);

                if (creation == null) return false;
                steps.set(i, step.withCreation(creation));

                continue;
            }

            if (step.resultType.getKind().isPrimitive() ||
                    !step.resolved.access().writable()) {

                continue;
            }

            TypeMirror createType = ensureCreationType(step, plan.method, generated);

            if (createType == null) return false;

            Creation creation = resolveContainerCreation(createType, next,
                    plan.pathParameters, plan.method, generated);

            if (creation == null) return false;
            steps.set(i, step.withCreation(creation));
        }

        return true;
    }

    /**
     * Selects a type that is both valid as the created runtime child and can be
     * assigned through the write side of the access.
     */
    private TypeMirror ensureCreationType(
            Step step,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeMirror readType = step.resolved.access().readType();
        TypeMirror writeType = step.resolved.access().writeType();

        if (types.isAssignableBoxedGeneric(
                readType,
                writeType)) {

            return readType;
        }

        if (types.isAssignableBoxedGeneric(
                writeType,
                readType)) {

            return writeType;
        }

        error(
                method,
                generated,
                "Ensure intermediate read/write type mismatch on " +
                        step.resolved.ownerType() +
                        ": cannot reconcile " +
                        readType +
                        " and " +
                        writeType);

        return null;
    }

    /*
     * --------------------------------------------------------------
     * Resolve
     * --------------------------------------------------------------
     */

    private Step resolveStep(
            TypeMirror ownerType,
            PathSegment segment,
            Map<String, VariableElement> parameters,
            ExecutableElement method,
            GeneratedClass generated,
            String annotation,
            boolean write) {

        ResolvedStep resolved;
        VariableElement parameter = null;

        if (segment instanceof PathSegment.Name || segment instanceof PathSegment.Index ||
                segment instanceof PathSegment.Append) {
            resolved = context.paths.resolveStep(ownerType, segment);

        } else if (segment instanceof PathSegment.Param) {
            String name = ((PathSegment.Param) segment).param;
            parameter = parameters.get(name);

            if (parameter == null) {
                error(
                        method,
                        generated,
                        annotation +
                                " path parameter '{" +
                                name +
                                "}' has no matching method parameter");

                return null;
            }

            if (isString(parameter.asType())) {
                resolved = context.paths.resolveParameter(ownerType, (PathSegment.Param) segment, true);

            } else if (parameter.asType().getKind() ==
                    TypeKind.INT) {

                resolved = context.paths.resolveParameter(ownerType, (PathSegment.Param) segment, false);

            } else {
                error(
                        method,
                        generated,
                        annotation +
                                " path parameter '" +
                                parameter.getSimpleName() +
                                "' must be String or int");

                return null;
            }

        } else {
            error(
                    method,
                    generated,
                    annotation +
                            " contains unsupported path segment " +
                            segment);

            return null;
        }

        NodeAccess access = resolved.access();
        if (access != null &&
                access.kind() == NodeAccess.Kind.MAP &&
                !types.hasSafeStringPathKey(ownerType)) {

            TypeMirror key = types.mapDeclaredKeyType(ownerType);

            error(
                    method,
                    generated,
                    annotation +
                            " cannot use a String path key with Map key type " +
                            (key == null ? "raw" : key));

            return null;
        }

        TypeMirror resultType = access == null ? null
                : write ? access.writeType() : access.readType();
        TypeMirror oldType = access == null || segment instanceof PathSegment.Append ? null
                : access.readable() ? access.readType() : types.objectType();
        return new Step(resolved, parameter, resultType, oldType, null);
    }

    /*
     * --------------------------------------------------------------
     * Create
     * --------------------------------------------------------------
     */

    private Creation resolveContainerCreation(
            TypeMirror type,
            PathSegment next,
            Map<String, VariableElement> parameters,
            ExecutableElement method,
            GeneratedClass generated) {

        boolean objectShape = isObjectSegment(next, parameters);

        if (type.getKind() == TypeKind.ARRAY) {
            error(
                    method,
                    generated,
                    "Cannot create ensure intermediate array container for " +
                            type);

            return null;
        }

        if (types.isObject(type)) {
            return objectShape
                    ? new Creation("java.util.LinkedHashMap", true)
                    : new Creation("java.util.ArrayList", true);
        }

        if (objectShape) {
            if (types.isSameErasure(type, mapType)) {

                return new Creation("java.util.LinkedHashMap", true);
            }

            if (types.isSameErasure(type, jsonObjectType)) {

                return new Creation("org.sjf4j.JsonObject", false);
            }

            return resolveDeclaredCreation(type, method, generated);
        }

        if (types.isSameErasure(type, listType)) {

            return new Creation("java.util.ArrayList", true);
        }

        if (types.isSameErasure(type, jsonArrayType)) {

            return new Creation("org.sjf4j.JsonArray", false);
        }

        if (types.isAssignableErasure(
                type,
                listType) ||
                types.isAssignableErasure(
                        type,
                        jsonArrayType) ||
                types.isExternalNode(type)) {

            return resolveDeclaredCreation(type, method, generated);
        }

        error(
                method,
                generated,
                "Unsupported ensure intermediate array container type " +
                        type);

        return null;
    }

    private Creation resolveDeclaredCreation(
            TypeMirror type,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeElement element = types.typeElement(type);

        if (element == null) {
            error(
                    method,
                    generated,
                    "Unsupported ensure intermediate container type " +
                            type);

            return null;
        }

        if (element.getKind() == ElementKind.INTERFACE ||
                element.getModifiers().contains(
                        Modifier.ABSTRACT)) {

            error(
                    method,
                    generated,
                    "Ensure intermediate type " +
                            type +
                            " is abstract or an interface");

            return null;
        }

        if (element.getNestingKind() ==
                NestingKind.MEMBER &&
                !element.getModifiers()
                        .contains(Modifier.STATIC)) {

            error(
                    method,
                    generated,
                    "Ensure intermediate type " +
                            type +
                            " is a non-static inner class");

            return null;
        }

        PackageElement generatedPackage = context.elements.getPackageOf(method);

        if (!isTypeAccessible(
                element,
                generatedPackage)) {

            error(
                    method,
                    generated,
                    "Ensure intermediate type " +
                            type +
                            " is not accessible from generated code");

            return null;
        }

        if (!hasAccessibleNoArgConstructor(
                element,
                generatedPackage)) {

            error(
                    method,
                    generated,
                    "Ensure intermediate type " +
                            type +
                            " must have an accessible no-arg constructor");

            return null;
        }

        String erased = context.typeUtils.erasure(type).toString();

        return new Creation(erased, !element.getTypeParameters().isEmpty());
    }

    private boolean isTypeAccessible(
            TypeElement type,
            PackageElement generatedPackage) {

        Element current = type;

        while (current instanceof TypeElement) {
            TypeElement element = (TypeElement) current;

            if (!element.getModifiers()
                    .contains(Modifier.PUBLIC)) {

                if (element.getModifiers()
                        .contains(Modifier.PRIVATE)) {

                    return false;
                }

                PackageElement ownerPackage = context.elements.getPackageOf(element);

                if (!generatedPackage.equals(
                        ownerPackage)) {

                    return false;
                }
            }

            current = element.getEnclosingElement();
        }

        return true;
    }

    private boolean hasAccessibleNoArgConstructor(
            TypeElement type,
            PackageElement generatedPackage) {

        boolean foundConstructor = false;

        PackageElement typePackage = context.elements.getPackageOf(type);
        boolean samePackage = generatedPackage.equals(typePackage);

        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() != ElementKind.CONSTRUCTOR) continue;

            foundConstructor = true;

            ExecutableElement constructor = (ExecutableElement) member;

            if (!constructor.getParameters()
                    .isEmpty()) {

                continue;
            }

            if (constructor.getModifiers()
                    .contains(Modifier.PUBLIC)) {

                return true;
            }

            if (samePackage &&
                    !constructor.getModifiers()
                            .contains(Modifier.PRIVATE)) {

                return true;
            }
        }

        /*
         * Defensive fallback for compiler models that do not expose the
         * implicit default constructor as an enclosed element.
         */
        return !foundConstructor;
    }

    private boolean isObjectSegment(
            PathSegment segment,
            Map<String, VariableElement> parameters) {

        if (segment instanceof PathSegment.Name) return true;

        if (segment instanceof PathSegment.Param) {
            VariableElement parameter = parameters.get(((PathSegment.Param) segment).param);

            return parameter != null &&
                    isString(
                            parameter.asType());
        }

        return false;
    }

    private boolean isString(TypeMirror type) {
        return types.isSameErasure(type, stringType);
    }

    private void accessError(
            ExecutableElement method,
            GeneratedClass generated,
            TypeMirror ownerType,
            Step step,
            boolean read) {

        String mode = read ? "readable" : "writable";

        if (step.resolved.segment() instanceof PathSegment.Name) {
            error(
                    method,
                    generated,
                    "Cannot resolve " +
                            mode +
                            " property '" +
                            ((PathSegment.Name)
                                    step.resolved.segment()).name +
                            "' on " +
                            ownerType);

            return;
        }

        if (step.resolved.segment() instanceof PathSegment.Append) {
            error(
                    method,
                    generated,
                    "Cannot append on " +
                            ownerType);

            return;
        }

        if (step.resolved.segment() instanceof PathSegment.Index) {
            error(
                    method,
                    generated,
                    "Cannot resolve " +
                            mode +
                            " index [" +
                            ((PathSegment.Index)
                                    step.resolved.segment()).index +
                            "] on " +
                            ownerType);

            return;
        }

        if (step.resolved.segment() instanceof PathSegment.Param) {
            String name = ((PathSegment.Param) step.resolved.segment()).param;

            if (step.parameter != null &&
                    isString(
                            step.parameter.asType())) {

                error(
                        method,
                        generated,
                        "Cannot resolve " +
                                mode +
                                " dynamic member '{" +
                                name +
                                "}' on " +
                                ownerType);

            } else {
                error(
                        method,
                        generated,
                        "Cannot resolve " +
                                mode +
                                " dynamic index '{" +
                                name +
                                "}' on " +
                                ownerType);
            }

            return;
        }

        error(
                method,
                generated,
                "Cannot resolve " +
                        mode +
                        " path access on " +
                        ownerType);
    }

    private void error(
            Element element,
            GeneratedClass generated,
            String message) {

        generated.invalidate();

        context.error(
                element,
                generated.originName() +
                        ": " +
                        message);
    }

    private TypeMirror type(String qualifiedName) {
        return context.elements.getTypeElement(qualifiedName).asType();
    }
}
