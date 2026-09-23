package org.sjf4j.processor.path;

import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.Property;
import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.type.TypeSystem;

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


/**
 * Compiles resolved navigator paths into direct Java source.
 *
 * <p>All structural resolution and validation is completed before the returned
 * member is added to the generated class. Source emission itself performs no
 * property or OBNT discovery.</p>
 */
final class PathEmitter {

    private final ProcessorContext context;
    private final TypeSystem types;

    private final TypeMirror stringType;
    private final TypeMirror mapType;
    private final TypeMirror listType;
    private final TypeMirror jsonObjectType;
    private final TypeMirror jsonArrayType;


    PathEmitter(ProcessorContext context) {
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

    GeneratedClass.Member compileGet(
            NavigatorMethodGenerator.GetPlan plan,
            GeneratedClass generated) {

        TypeMirror currentType =
                plan.root.asType();

        List<Step> steps =
                new ArrayList<Step>();

        PathSegment[] segments =
                plan.path.segments();

        for (int i = 1; i < segments.length; i++) {
            Step step =
                    resolveStep(
                            currentType,
                            segments[i],
                            plan.pathParameters,
                            plan.method,
                            generated,
                            "@GetByPath");

            if (step == null) {
                return null;
            }

            if (step.access == null ||
                    !step.access.readable()) {

                accessError(
                        plan.method,
                        generated,
                        currentType,
                        step,
                        true);

                return null;
            }

            step.resultType =
                    step.access.readType();

            steps.add(step);

            currentType =
                    step.resultType;
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

        ResolvedGet resolved =
                new ResolvedGet(
                        plan,
                        steps,
                        currentType);

        return out ->
                emitGet(out, resolved);
    }


    /*
     * --------------------------------------------------------------
     * Compile Put
     * --------------------------------------------------------------
     */

    GeneratedClass.Member compilePut(
            NavigatorMethodGenerator.PutPlan plan,
            GeneratedClass generated) {

        TypeMirror currentType =
                plan.root.asType();

        PathSegment[] segments =
                plan.path.segments();

        List<Step> steps =
                new ArrayList<Step>();

        /*
         * Phase 1:
         * resolve the complete intermediate path.
         */
        for (int i = 1; i < segments.length - 1; i++) {
            PathSegment segment =
                    segments[i];

            Step step =
                    resolveStep(
                            currentType,
                            segment,
                            plan.pathParameters,
                            plan.method,
                            generated,
                            plan.annotation);

            if (step == null) {
                return null;
            }

            if (segment instanceof PathSegment.Append) {
                if (!plan.ensure) {
                    error(
                            plan.method,
                            generated,
                            plan.annotation +
                                    " append segment must be the final path segment");
                    return null;
                }

                if (step.access == null ||
                        !step.access.writable()) {

                    accessError(
                            plan.method,
                            generated,
                            currentType,
                            step,
                            false);

                    return null;
                }

                step.resultType =
                        step.access.writeType();

            } else {
                if (step.access == null ||
                        !step.access.readable()) {

                    accessError(
                            plan.method,
                            generated,
                            currentType,
                            step,
                            true);

                    return null;
                }

                if (plan.ensure &&
                        (step.access.kind() == NodeAccess.Kind.LIST ||
                                step.access.kind() == NodeAccess.Kind.MAP) &&
                        step.access.writeType() == null) {

                    error(
                            plan.method,
                            generated,
                            plan.annotation +
                                    " cannot materialize through wildcard container " +
                                    step.ownerType);

                    return null;
                }

                step.resultType =
                        step.access.readType();
            }

            steps.add(step);

            currentType =
                    step.resultType;
        }

        /*
         * Resolve final write target.
         */
        Step target =
                resolveStep(
                        currentType,
                        segments[segments.length - 1],
                        plan.pathParameters,
                        plan.method,
                        generated,
                        plan.annotation);

        if (target == null) {
            return null;
        }

        if (target.access == null ||
                !target.access.writable()) {

            accessError(
                    plan.method,
                    generated,
                    currentType,
                    target,
                    false);

            return null;
        }

        if (plan.ifAbsent &&
                !(target.segment instanceof PathSegment.Append)) {

            if (!target.access.readable()) {
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
            if (target.access
                    .readType()
                    .getKind()
                    .isPrimitive()) {

                error(
                        plan.method,
                        generated,
                        plan.annotation +
                                " cannot target primitive value " +
                                target.access.readType());

                return null;
            }
        }

        TypeMirror valueType =
                target.access.writeType();

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

        TypeMirror oldType =
                oldType(target);

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

        ResolvedPut resolved =
                new ResolvedPut(
                        plan,
                        steps,
                        currentType,
                        target,
                        valueType,
                        oldType);

        return out ->
                emitPut(out, resolved);
    }


    private boolean validatePutReturn(
            NavigatorMethodGenerator.PutPlan plan,
            Step target,
            TypeMirror oldType,
            GeneratedClass generated) {

        TypeMirror returnType =
                plan.method.getReturnType();

        if (returnType.getKind() == TypeKind.VOID) {
            return true;
        }

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
            boolean nullable =
                    plan.ifParentPresent ||
                            plan.ifAbsent ||
                            !oldType.getKind().isPrimitive();

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
            NavigatorMethodGenerator.PutPlan plan,
            List<Step> steps,
            Step target,
            GeneratedClass generated) {

        for (int i = 0; i < steps.size(); i++) {
            Step step =
                    steps.get(i);

            PathSegment next =
                    i + 1 < steps.size()
                            ? steps.get(i + 1).segment
                            : target.segment;

            if (step.segment instanceof PathSegment.Append) {
                step.createExpression =
                        resolveContainerCreation(
                                step.resultType,
                                next,
                                plan.pathParameters,
                                plan.method,
                                generated);

                if (step.createExpression == null) {
                    return false;
                }

                continue;
            }

            if (step.resultType.getKind().isPrimitive() ||
                    !step.access.writable()) {

                continue;
            }

            TypeMirror createType =
                    ensureCreationType(
                            step,
                            plan.method,
                            generated);

            if (createType == null) {
                return false;
            }

            step.createExpression =
                    resolveContainerCreation(
                            createType,
                            next,
                            plan.pathParameters,
                            plan.method,
                            generated);

            if (step.createExpression == null) {
                return false;
            }
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

        TypeMirror readType =
                step.access.readType();

        TypeMirror writeType =
                step.access.writeType();

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
                        step.ownerType +
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
            String annotation) {

        NodeAccess access;
        VariableElement parameter = null;

        if (segment instanceof PathSegment.Name) {
            access =
                    context.access.resolveName(
                            ownerType,
                            ((PathSegment.Name) segment).name);

        } else if (segment instanceof PathSegment.Index) {
            access =
                    context.access.resolveIndex(
                            ownerType);

        } else if (segment instanceof PathSegment.Append) {
            access =
                    context.access.resolveAppend(
                            ownerType);

        } else if (segment instanceof PathSegment.Param) {
            String name =
                    ((PathSegment.Param) segment).param;

            parameter =
                    parameters.get(name);

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
                access =
                        context.access.resolveDynamicName(
                                ownerType);

            } else if (parameter.asType().getKind() ==
                    TypeKind.INT) {

                access =
                        context.access.resolveIndex(
                                ownerType);

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

        if (access != null &&
                access.kind() == NodeAccess.Kind.MAP &&
                !types.hasSafeStringPathKey(ownerType)) {

            TypeMirror key =
                    types.mapDeclaredKeyType(ownerType);

            error(
                    method,
                    generated,
                    annotation +
                            " cannot use a String path key with Map key type " +
                            (key == null ? "raw" : key));

            return null;
        }

        return new Step(
                ownerType,
                segment,
                parameter,
                access);
    }


    /*
     * --------------------------------------------------------------
     * Emit
     * --------------------------------------------------------------
     */

    private void emitGet(
            JavaWriter out,
            ResolvedGet resolved) {

        ExecutableElement method =
                resolved.plan.method;

        NameAllocator names =
                names(method);

        out.line("@Override");
        out.beginBlock(
                methodHeader(method));

        String root =
                resolved.plan.root
                        .getSimpleName()
                        .toString();

        String missing =
                getMissing(
                        method,
                        resolved.plan);

        if (!resolved.plan.root
                .asType()
                .getKind()
                .isPrimitive()) {

            out.line(
                    "if (" +
                            root +
                            " == null) " +
                            missing);
        }

        String current =
                root;

        for (Step step : resolved.steps) {
            String next =
                    names.newName(
                            stepHint(step));

            emitRead(
                    out,
                    step,
                    current,
                    next,
                    names,
                    ReadMode.STRICT,
                    missing);

            current =
                    next;
        }

        out.line(
                "return " +
                        current +
                        ";");

        out.endBlock();
    }


    /*
     * --------------------------------------------------------------
     * Emit Put
     * --------------------------------------------------------------
     */

    private void emitPut(
            JavaWriter out,
            ResolvedPut resolved) {

        if (resolved.plan.ensure) {
            emitEnsurePut(
                    out,
                    resolved);
        } else {
            emitPlainPut(
                    out,
                    resolved);
        }
    }


    private void emitPlainPut(
            JavaWriter out,
            ResolvedPut resolved) {

        NavigatorMethodGenerator.PutPlan plan =
                resolved.plan;

        ExecutableElement method =
                plan.method;

        NameAllocator names =
                names(method);

        out.line("@Override");
        out.beginBlock(
                methodHeader(method));

        String root =
                plan.root
                        .getSimpleName()
                        .toString();

        String value =
                plan.value
                        .getSimpleName()
                        .toString();

        String missing =
                plan.ifParentPresent
                        ? nullReturn(method)
                        : putMissingThrow(plan);

        if (!plan.root
                .asType()
                .getKind()
                .isPrimitive()) {

            out.line(
                    "if (" +
                            root +
                            " == null) " +
                            missing);
        }

        String current =
                root;

        for (Step step : resolved.steps) {
            String next =
                    names.newName(
                            stepHint(step));

            emitRead(
                    out,
                    step,
                    current,
                    next,
                    names,
                    ReadMode.STRICT,
                    missing);

            current =
                    next;
        }

        boolean needOld =
                method.getReturnType().getKind() != TypeKind.VOID &&
                        resolved.oldType != null;

        String old =
                needOld
                        ? names.newName("oldValue")
                        : null;

        emitWrite(
                out,
                resolved.target,
                current,
                value,
                old,
                names);

        emitOldReturn(
                out,
                method,
                old,
                resolved.oldType);

        out.endBlock();
    }


    private void emitEnsurePut(
            JavaWriter out,
            ResolvedPut resolved) {

        NavigatorMethodGenerator.PutPlan plan =
                resolved.plan;

        ExecutableElement method =
                plan.method;

        NameAllocator names =
                names(method);

        out.line("@Override");
        out.beginBlock(
                methodHeader(method));

        String root =
                plan.root
                        .getSimpleName()
                        .toString();

        String value =
                plan.value
                        .getSimpleName()
                        .toString();

        out.line(
                "java.util.Objects.requireNonNull(" +
                        root +
                        ", " +
                        JavaWriter.stringLiteral(root) +
                        ");");

        String current =
                root;

        for (Step step : resolved.steps) {
            String next =
                    names.newName(
                            stepHint(step));

            /*
             * Intermediate append creates a fresh child immediately.
             */
            if (step.segment instanceof PathSegment.Append) {
                out.line(
                        localType(step.resultType) +
                                " " +
                                next +
                                " = " +
                                step.createExpression +
                                ";");

                emitWrite(
                        out,
                        step,
                        current,
                        next,
                        null,
                        names);

                current = next;
                continue;
            }

            emitRead(
                    out,
                    step,
                    current,
                    next,
                    names,
                    ReadMode.ENSURE,
                    null);

            if (!step.resultType
                    .getKind()
                    .isPrimitive()) {

                out.beginBlock(
                        "if (" +
                                next +
                                " == null)");

                if (step.access.writable()) {
                    if (step.createExpression == null) {
                        throw new AssertionError(
                                "Missing ensure creation expression");
                    }

                    out.line(
                            next +
                                    " = " +
                                    step.createExpression +
                                    ";");

                    emitWrite(
                            out,
                            step,
                            current,
                            next,
                            null,
                            names);

                } else {
                    emitReadonlyEnsureFailure(
                            out,
                            step);
                }

                out.endBlock();
            }

            current =
                    next;
        }

        if (plan.ifAbsent) {
            emitIfAbsent(
                    out,
                    resolved,
                    current,
                    value,
                    names);

        } else {
            boolean needOld =
                    method.getReturnType().getKind() != TypeKind.VOID &&
                            resolved.oldType != null;

            String old =
                    needOld
                            ? names.newName("oldValue")
                            : null;

            emitWrite(
                    out,
                    resolved.target,
                    current,
                    value,
                    old,
                    names);

            emitOldReturn(
                    out,
                    method,
                    old,
                    resolved.oldType);
        }

        out.endBlock();
    }


    /*
     * --------------------------------------------------------------
     * Path Read
     * --------------------------------------------------------------
     */

    private void emitRead(
            JavaWriter out,
            Step step,
            String owner,
            String target,
            NameAllocator names,
            ReadMode mode,
            String missing) {

        TypeMirror type =
                step.access.readType();

        switch (step.access.kind()) {
            case PROPERTY:
                emitPropertyRead(
                        out,
                        step.access.property(),
                        owner,
                        target,
                        type);
                break;

            case MAP:
                out.line(
                        localType(type) +
                                " " +
                                target +
                                " = " +
                                owner +
                                ".get(" +
                                nameExpression(step) +
                                ");");
                break;

            case JSON_OBJECT:
                emitObjectResult(
                        out,
                        type,
                        target,
                        owner +
                                ".getNode(" +
                                nameExpression(step) +
                                ")");
                break;

            case LIST:
                emitIndexedRead(
                        out,
                        step,
                        owner,
                        target,
                        names,
                        type,
                        mode,
                        missing,
                        false,
                        false);
                break;

            case ARRAY:
                emitIndexedRead(
                        out,
                        step,
                        owner,
                        target,
                        names,
                        type,
                        mode,
                        missing,
                        true,
                        false);
                break;

            case JSON_ARRAY:
                emitIndexedRead(
                        out,
                        step,
                        owner,
                        target,
                        names,
                        type,
                        mode,
                        missing,
                        false,
                        true);
                break;

            case DYNAMIC:
                if (isIndexStep(step)) {
                    emitObjectResult(
                            out,
                            type,
                            target,
                            "org.sjf4j.Nodes.getInArray(" +
                                    owner +
                                    ", " +
                                    rawIndexExpression(step) +
                                    ")");
                } else {
                    emitObjectResult(
                            out,
                            type,
                            target,
                            "org.sjf4j.Nodes.getInObject(" +
                                    owner +
                                    ", " +
                                    nameExpression(step) +
                                    ")");
                }
                break;

            default:
                throw new AssertionError(
                        "Unsupported NodeAccess kind: " +
                                step.access.kind());
        }

        if (mode == ReadMode.STRICT &&
                !type.getKind().isPrimitive()) {

            out.line(
                    "if (" +
                            target +
                            " == null) " +
                            missing);
        }
    }


    private void emitPropertyRead(
            JavaWriter out,
            Property property,
            String owner,
            String target,
            TypeMirror type) {

        PropertyAccess read =
                property.read();

        String expression;

        if (read.isMethod()) {
            expression =
                    owner +
                            "." +
                            read.memberName() +
                            "()";
        } else {
            expression =
                    owner +
                            "." +
                            read.memberName();
        }

        out.line(
                localType(type) +
                        " " +
                        target +
                        " = " +
                        expression +
                        ";");
    }


    private void emitIndexedRead(
            JavaWriter out,
            Step step,
            String owner,
            String target,
            NameAllocator names,
            TypeMirror type,
            ReadMode mode,
            String missing,
            boolean array,
            boolean jsonArray) {

        String size =
                array
                        ? owner + ".length"
                        : owner + ".size()";

        String index =
                emitIndex(
                        out,
                        step,
                        size,
                        names);

        String expression;

        if (array) {
            expression =
                    owner +
                            "[" +
                            index +
                            "]";

        } else if (jsonArray) {
            expression =
                    objectExpression(
                            type,
                            owner +
                                    ".getNode(" +
                                    index +
                                    ")");

        } else {
            expression =
                    owner +
                            ".get(" +
                            index +
                            ")";
        }

        if (mode == ReadMode.ENSURE) {
            out.line(
                    localType(type) +
                            " " +
                            target +
                            " = (" +
                            index +
                            " < 0 || " +
                            index +
                            " >= " +
                            size +
                            ") ? null : " +
                            expression +
                            ";");

            return;
        }

        out.line(
                "if (" +
                        index +
                        " < 0 || " +
                        index +
                        " >= " +
                        size +
                        ") " +
                        missing);

        out.line(
                localType(type) +
                        " " +
                        target +
                        " = " +
                        expression +
                        ";");
    }


    private void emitObjectResult(
            JavaWriter out,
            TypeMirror type,
            String target,
            String expression) {

        out.line(
                localType(type) +
                        " " +
                        target +
                        " = " +
                        objectExpression(
                                type,
                                expression) +
                        ";");
    }


    private String objectExpression(
            TypeMirror type,
            String expression) {

        if (types.isObject(type)) {
            return expression;
        }

        return "(" +
                localType(type) +
                ") " +
                expression;
    }


    /*
     * --------------------------------------------------------------
     * Path Write
     * --------------------------------------------------------------
     */

    /**
     * Emits one write. When {@code old} is null the previous value is not read
     * or captured.
     */
    private void emitWrite(
            JavaWriter out,
            Step step,
            String owner,
            String value,
            String old,
            NameAllocator names) {

        if (step.segment instanceof PathSegment.Append) {
            emitAppend(
                    out,
                    step,
                    owner,
                    value);
            return;
        }

        switch (step.access.kind()) {
            case PROPERTY:
                if (old != null) {
                    emitPropertyOld(
                            out,
                            step,
                            owner,
                            old);
                }

                emitPropertyWrite(
                        out,
                        step.access.property(),
                        owner,
                        value);
                return;

            case MAP: {
                String expression =
                        owner +
                                ".put(" +
                                nameExpression(step) +
                                ", " +
                                value +
                                ")";

                emitOptionalOld(
                        out,
                        step,
                        old,
                        expression);

                return;
            }

            case JSON_OBJECT: {
                String expression =
                        owner +
                                ".put(" +
                                nameExpression(step) +
                                ", " +
                                value +
                                ")";

                emitOptionalObjectOld(
                        out,
                        step,
                        old,
                        expression);

                return;
            }

            case LIST:
            case ARRAY:
            case JSON_ARRAY:
                emitIndexWrite(
                        out,
                        step,
                        owner,
                        value,
                        old,
                        names);
                return;

            case DYNAMIC: {
                String expression;

                if (isIndexStep(step)) {
                    expression =
                            "org.sjf4j.Nodes.putInArray(" +
                                    owner +
                                    ", " +
                                    rawIndexExpression(step) +
                                    ", " +
                                    value +
                                    ")";

                } else {
                    expression =
                            "org.sjf4j.Nodes.putInObject(" +
                                    owner +
                                    ", " +
                                    nameExpression(step) +
                                    ", " +
                                    value +
                                    ")";
                }

                emitOptionalObjectOld(
                        out,
                        step,
                        old,
                        expression);

                return;
            }

            default:
                throw new AssertionError(
                        "Unsupported NodeAccess kind: " +
                                step.access.kind());
        }
    }


    private void emitPropertyOld(
            JavaWriter out,
            Step step,
            String owner,
            String old) {

        if (!step.access.readable()) {
            out.line(
                    "Object " +
                            old +
                            " = null;");
            return;
        }

        emitPropertyRead(
                out,
                step.access.property(),
                owner,
                old,
                step.access.readType());
    }


    private void emitPropertyWrite(
            JavaWriter out,
            Property property,
            String owner,
            String value) {

        PropertyAccess write =
                property.write();

        if (write.isMethod()) {
            out.line(
                    owner +
                            "." +
                            write.memberName() +
                            "(" +
                            value +
                            ");");
        } else {
            out.line(
                    owner +
                            "." +
                            write.memberName() +
                            " = " +
                            value +
                            ";");
        }
    }


    private void emitOptionalOld(
            JavaWriter out,
            Step step,
            String old,
            String expression) {

        if (old == null) {
            out.line(
                    expression +
                            ";");
            return;
        }

        out.line(
                localType(oldType(step)) +
                        " " +
                        old +
                        " = " +
                        expression +
                        ";");
    }


    private void emitOptionalObjectOld(
            JavaWriter out,
            Step step,
            String old,
            String expression) {

        if (old == null) {
            out.line(
                    expression +
                            ";");
            return;
        }

        TypeMirror type =
                oldType(step);

        out.line(
                localType(type) +
                        " " +
                        old +
                        " = " +
                        objectExpression(
                                type,
                                expression) +
                        ";");
    }


    /*
     * --------------------------------------------------------------
     * Indexed Write
     * --------------------------------------------------------------
     */

    private void emitIndexWrite(
            JavaWriter out,
            Step step,
            String owner,
            String value,
            String old,
            NameAllocator names) {

        if (step.segment instanceof PathSegment.Append) {
            throw new AssertionError(
                    "Append must use emitAppend");
        }

        boolean array =
                step.access.kind() ==
                        NodeAccess.Kind.ARRAY;

        String size =
                array
                        ? owner + ".length"
                        : owner + ".size()";

        String index =
                emitIndex(
                        out,
                        step,
                        size,
                        names);

        if (array) {
            out.line(
                    "if (" +
                            index +
                            " == " +
                            owner +
                            ".length) " +
                            "throw new org.sjf4j.exception.JsonException(" +
                            JavaWriter.stringLiteral(
                                    "cannot append to a Java array") +
                            ");");

            out.line(
                    "if (" +
                            index +
                            " < 0 || " +
                            index +
                            " >= " +
                            owner +
                            ".length) " +
                            "throw new org.sjf4j.exception.JsonException(" +
                            JavaWriter.stringLiteral(
                                    "invalid Java array index") +
                            ");");

            if (old != null) {
                out.line(
                        localType(oldType(step)) +
                                " " +
                                old +
                                " = " +
                                owner +
                                "[" +
                                index +
                                "];");
            }

            out.line(
                    owner +
                            "[" +
                            index +
                            "] = " +
                            value +
                            ";");

            return;
        }

        if (old != null) {
            out.line(
                    localType(oldType(step)) +
                            " " +
                            old +
                            ";");
        }

        out.beginBlock(
                "if (" +
                        index +
                        " >= 0 && " +
                        index +
                        " < " +
                        owner +
                        ".size())");

        if (old != null) {
            out.line(
                    old +
                            " = (" +
                            localType(oldType(step)) +
                            ") " +
                            owner +
                            ".set(" +
                            index +
                            ", " +
                            value +
                            ");");
        } else {
            out.line(
                    owner +
                            ".set(" +
                            index +
                            ", " +
                            value +
                            ");");
        }

        out.endBlock(
                " else if (" +
                        index +
                        " == " +
                        owner +
                        ".size()) {");

        out.indent();

        out.line(
                owner +
                        ".add(" +
                        value +
                        ");");

        if (old != null) {
            out.line(
                    old +
                            " = null;");
        }

        out.endBlock(" else {");
        out.indent();

        String container =
                step.access.kind() ==
                        NodeAccess.Kind.JSON_ARRAY
                        ? "JsonArray"
                        : "List";

        out.line(
                "throw new org.sjf4j.exception.JsonException(" +
                        JavaWriter.stringLiteral(
                                "cannot set at index ") +
                        " + " +
                        index +
                        " + " +
                        JavaWriter.stringLiteral(
                                " in " +
                                        container +
                                        " of size ") +
                        " + " +
                        owner +
                        ".size());");

        out.endBlock();
    }


    private void emitAppend(
            JavaWriter out,
            Step step,
            String owner,
            String value) {

        switch (step.access.kind()) {
            case LIST:
            case JSON_ARRAY:
                out.line(
                        owner +
                                ".add(" +
                                value +
                                ");");
                return;

            case DYNAMIC:
                out.line(
                        "org.sjf4j.Nodes.addInArray(" +
                                owner +
                                ", " +
                                value +
                                ");");
                return;

            default:
                throw new AssertionError(
                        "Cannot append using " +
                                step.access.kind());
        }
    }


    /*
     * --------------------------------------------------------------
     * Put If Absent
     * --------------------------------------------------------------
     */

    private void emitIfAbsent(
            JavaWriter out,
            ResolvedPut resolved,
            String owner,
            String value,
            NameAllocator names) {

        Step target =
                resolved.target;

        if (target.segment instanceof PathSegment.Append) {
            emitWrite(
                    out,
                    target,
                    owner,
                    value,
                    null,
                    names);

            emitNullReturn(
                    out,
                    resolved.plan.method);

            return;
        }

        String old =
                names.newName("oldValue");

        emitRead(
                out,
                target,
                owner,
                old,
                names,
                ReadMode.ENSURE,
                null);

        out.beginBlock(
                "if (" +
                        old +
                        " != null)");

        if (resolved.plan.method
                .getReturnType()
                .getKind() == TypeKind.VOID) {

            out.line("return;");
        } else {
            out.line(
                    "return " +
                            old +
                            ";");
        }

        out.endBlock();

        emitWrite(
                out,
                target,
                owner,
                value,
                null,
                names);

        emitNullReturn(
                out,
                resolved.plan.method);
    }


    /*
     * --------------------------------------------------------------
     * Create
     * --------------------------------------------------------------
     */

    private String resolveContainerCreation(
            TypeMirror type,
            PathSegment next,
            Map<String, VariableElement> parameters,
            ExecutableElement method,
            GeneratedClass generated) {

        boolean objectShape =
                isObjectSegment(
                        next,
                        parameters);

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
                    ? "new java.util.LinkedHashMap<>()"
                    : "new java.util.ArrayList<>()";
        }

        if (objectShape) {
            if (types.isSameErasure(
                    type,
                    mapType)) {

                return "new java.util.LinkedHashMap<>()";
            }

            if (types.isSameErasure(
                    type,
                    jsonObjectType)) {

                return "new org.sjf4j.JsonObject()";
            }

            return newDeclared(
                    type,
                    method,
                    generated);
        }

        if (types.isSameErasure(
                type,
                listType)) {

            return "new java.util.ArrayList<>()";
        }

        if (types.isSameErasure(
                type,
                jsonArrayType)) {

            return "new org.sjf4j.JsonArray()";
        }

        if (types.isAssignableErasure(
                type,
                listType) ||
                types.isAssignableErasure(
                        type,
                        jsonArrayType) ||
                types.isExternalNode(type)) {

            return newDeclared(
                    type,
                    method,
                    generated);
        }

        error(
                method,
                generated,
                "Unsupported ensure intermediate array container type " +
                        type);

        return null;
    }


    private String newDeclared(
            TypeMirror type,
            ExecutableElement method,
            GeneratedClass generated) {

        TypeElement element =
                types.typeElement(type);

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

        PackageElement generatedPackage =
                context.elements.getPackageOf(method);

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

        String erased =
                context.typeUtils
                        .erasure(type)
                        .toString();

        return "new " +
                erased +
                (element.getTypeParameters().isEmpty()
                        ? "()"
                        : "<>()");
    }


    private boolean isTypeAccessible(
            TypeElement type,
            PackageElement generatedPackage) {

        Element current =
                type;

        while (current instanceof TypeElement) {
            TypeElement element =
                    (TypeElement) current;

            if (!element.getModifiers()
                    .contains(Modifier.PUBLIC)) {

                if (element.getModifiers()
                        .contains(Modifier.PRIVATE)) {

                    return false;
                }

                PackageElement ownerPackage =
                        context.elements.getPackageOf(element);

                if (!generatedPackage.equals(
                        ownerPackage)) {

                    return false;
                }
            }

            current =
                    element.getEnclosingElement();
        }

        return true;
    }


    private boolean hasAccessibleNoArgConstructor(
            TypeElement type,
            PackageElement generatedPackage) {

        boolean foundConstructor = false;

        PackageElement typePackage =
                context.elements.getPackageOf(type);

        boolean samePackage =
                generatedPackage.equals(typePackage);

        for (Element member :
                type.getEnclosedElements()) {

            if (member.getKind() !=
                    ElementKind.CONSTRUCTOR) {

                continue;
            }

            foundConstructor = true;

            ExecutableElement constructor =
                    (ExecutableElement) member;

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


    /*
     * --------------------------------------------------------------
     * Path Arguments
     * --------------------------------------------------------------
     */

    private String emitIndex(
            JavaWriter out,
            Step step,
            String size,
            NameAllocator names) {

        String index =
                names.newName("index");

        if (step.segment instanceof PathSegment.Index) {
            int value =
                    ((PathSegment.Index)
                            step.segment).index;

            out.line(
                    "int " +
                            index +
                            " = " +
                            staticIndex(
                                    value,
                                    size) +
                            ";");

        } else {
            String parameter =
                    step.parameter
                            .getSimpleName()
                            .toString();

            out.line(
                    "int " +
                            index +
                            " = " +
                            parameter +
                            " >= 0 ? " +
                            parameter +
                            " : " +
                            size +
                            " + " +
                            parameter +
                            ";");
        }

        return index;
    }


    private static String staticIndex(
            int index,
            String size) {

        if (index >= 0) {
            return Integer.toString(index);
        }

        if (index == Integer.MIN_VALUE) {
            return size +
                    " + " +
                    index;
        }

        return size +
                " - " +
                (-index);
    }


    private String nameExpression(
            Step step) {

        if (step.segment instanceof PathSegment.Name) {
            return JavaWriter.stringLiteral(
                    ((PathSegment.Name)
                            step.segment).name);
        }

        return step.parameter
                .getSimpleName()
                .toString();
    }


    private String rawIndexExpression(
            Step step) {

        if (step.segment instanceof PathSegment.Index) {
            return Integer.toString(
                    ((PathSegment.Index)
                            step.segment).index);
        }

        return step.parameter
                .getSimpleName()
                .toString();
    }


    private boolean isIndexStep(
            Step step) {

        if (step.segment instanceof PathSegment.Index ||
                step.segment instanceof PathSegment.Append) {

            return true;
        }

        return step.segment instanceof PathSegment.Param &&
                step.parameter != null &&
                step.parameter
                        .asType()
                        .getKind() == TypeKind.INT;
    }


    private boolean isObjectSegment(
            PathSegment segment,
            Map<String, VariableElement> parameters) {

        if (segment instanceof PathSegment.Name) {
            return true;
        }

        if (segment instanceof PathSegment.Param) {
            VariableElement parameter =
                    parameters.get(
                            ((PathSegment.Param)
                                    segment).param);

            return parameter != null &&
                    isString(
                            parameter.asType());
        }

        return false;
    }


    private String methodHeader(
            ExecutableElement method) {

        StringBuilder out =
                new StringBuilder();

        out.append("public ")
                .append(method.getReturnType())
                .append(' ')
                .append(method.getSimpleName())
                .append('(');

        List<? extends VariableElement> parameters =
                method.getParameters();

        for (int i = 0; i < parameters.size(); i++) {
            if (i != 0) {
                out.append(", ");
            }

            VariableElement parameter =
                    parameters.get(i);

            out.append(parameter.asType())
                    .append(' ')
                    .append(parameter.getSimpleName());
        }

        out.append(')');

        return out.toString();
    }


    private NameAllocator names(
            ExecutableElement method) {

        NameAllocator names =
                new NameAllocator();

        for (VariableElement parameter :
                method.getParameters()) {

            names.reserve(
                    parameter
                            .getSimpleName()
                            .toString());
        }

        return names;
    }


    /*
     * --------------------------------------------------------------
     * Return Values
     * --------------------------------------------------------------
     */

    private String getMissing(
            ExecutableElement method,
            NavigatorMethodGenerator.GetPlan plan) {

        if (!method.getReturnType()
                .getKind()
                .isPrimitive()) {

            return "return null;";
        }

        return "throw new org.sjf4j.exception.JsonException(" +
                JavaWriter.stringLiteral(
                        "@GetByPath primitive result is missing: method " +
                                method.getSimpleName() +
                                " returns " +
                                method.getReturnType() +
                                " for path " +
                                plan.path.toExpr()) +
                ");";
    }


    private String putMissingThrow(
            NavigatorMethodGenerator.PutPlan plan) {

        return "throw new org.sjf4j.exception.JsonException(" +
                JavaWriter.stringLiteral(
                        plan.annotation +
                                " missing parent: method " +
                                plan.method.getSimpleName() +
                                " for path " +
                                plan.path.toExpr()) +
                ");";
    }


    private String nullReturn(
            ExecutableElement method) {

        return method.getReturnType()
                .getKind() == TypeKind.VOID
                ? "return;"
                : "return null;";
    }


    private void emitOldReturn(
            JavaWriter out,
            ExecutableElement method,
            String old,
            TypeMirror oldType) {

        if (method.getReturnType()
                .getKind() == TypeKind.VOID) {

            return;
        }

        if (oldType == null) {
            out.line("return null;");
            return;
        }

        if (old == null) {
            throw new AssertionError(
                    "Old value variable was not emitted");
        }

        out.line(
                "return " +
                        old +
                        ";");
    }


    private void emitNullReturn(
            JavaWriter out,
            ExecutableElement method) {

        if (method.getReturnType()
                .getKind() == TypeKind.VOID) {

            out.line("return;");
        } else {
            out.line("return null;");
        }
    }


    private TypeMirror oldType(
            Step step) {

        if (step.segment instanceof PathSegment.Append) {
            return null;
        }

        if (step.access.readable()) {
            return step.access.readType();
        }

        /*
         * Write-only property:
         * previous value is unavailable.
         */
        return types.objectType();
    }


    /*
     * --------------------------------------------------------------
     * Helpers
     * --------------------------------------------------------------
     */

    private String localType(
            TypeMirror type) {

        return types.concrete(type)
                .toString();
    }


    private boolean isString(
            TypeMirror type) {

        return types.isSameErasure(
                type,
                stringType);
    }


    private String stepHint(
            Step step) {

        if (step.segment instanceof PathSegment.Name) {
            return ((PathSegment.Name)
                    step.segment).name;
        }

        if (step.segment instanceof PathSegment.Param) {
            return ((PathSegment.Param)
                    step.segment).param;
        }

        if (step.segment instanceof PathSegment.Append) {
            return "value";
        }

        return "item";
    }


    private void emitReadonlyEnsureFailure(
            JavaWriter out,
            Step step) {

        out.line(
                "throw new org.sjf4j.exception.JsonException(" +
                        JavaWriter.stringLiteral(
                                "Cannot create missing ensure intermediate '" +
                                        stepHint(step) +
                                        "' on " +
                                        step.ownerType +
                                        ": access is read-only") +
                        ");");
    }


    private void accessError(
            ExecutableElement method,
            GeneratedClass generated,
            TypeMirror ownerType,
            Step step,
            boolean read) {

        String mode =
                read
                        ? "readable"
                        : "writable";

        if (step.segment instanceof PathSegment.Name) {
            error(
                    method,
                    generated,
                    "Cannot resolve " +
                            mode +
                            " property '" +
                            ((PathSegment.Name)
                                    step.segment).name +
                            "' on " +
                            ownerType);

            return;
        }

        if (step.segment instanceof PathSegment.Append) {
            error(
                    method,
                    generated,
                    "Cannot append on " +
                            ownerType);

            return;
        }

        if (step.segment instanceof PathSegment.Index) {
            error(
                    method,
                    generated,
                    "Cannot resolve " +
                            mode +
                            " index [" +
                            ((PathSegment.Index)
                                    step.segment).index +
                            "] on " +
                            ownerType);

            return;
        }

        if (step.segment instanceof PathSegment.Param) {
            String name =
                    ((PathSegment.Param)
                            step.segment).param;

            if (step.parameter != null &&
                    isString(
                            step.parameter.asType())) {

                error(
                        method,
                        generated,
                        "Cannot resolve " +
                                mode +
                                " dynamic property '{" +
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


    private TypeMirror type(
            String qualifiedName) {

        return context.elements
                .getTypeElement(qualifiedName)
                .asType();
    }


    /*
     * --------------------------------------------------------------
     * Resolved Paths
     * --------------------------------------------------------------
     */

    private enum ReadMode {
        STRICT,
        ENSURE
    }


    private static final class Step {

        final TypeMirror ownerType;
        final PathSegment segment;
        final VariableElement parameter;
        final NodeAccess access;

        TypeMirror resultType;
        String createExpression;


        Step(
                TypeMirror ownerType,
                PathSegment segment,
                VariableElement parameter,
                NodeAccess access) {

            this.ownerType = ownerType;
            this.segment = segment;
            this.parameter = parameter;
            this.access = access;
        }
    }


    private static final class ResolvedGet {

        final NavigatorMethodGenerator.GetPlan plan;
        final List<Step> steps;
        final TypeMirror resultType;


        ResolvedGet(
                NavigatorMethodGenerator.GetPlan plan,
                List<Step> steps,
                TypeMirror resultType) {

            this.plan = plan;
            this.steps = steps;
            this.resultType = resultType;
        }
    }


    private static final class ResolvedPut {

        final NavigatorMethodGenerator.PutPlan plan;

        final List<Step> steps;

        final TypeMirror parentType;
        final Step target;

        final TypeMirror valueType;
        final TypeMirror oldType;


        ResolvedPut(
                NavigatorMethodGenerator.PutPlan plan,
                List<Step> steps,
                TypeMirror parentType,
                Step target,
                TypeMirror valueType,
                TypeMirror oldType) {

            this.plan = plan;
            this.steps = steps;
            this.parentType = parentType;
            this.target = target;
            this.valueType = valueType;
            this.oldType = oldType;
        }
    }
}
