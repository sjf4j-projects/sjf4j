package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.path.PathSegment;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.access.NodeAccess;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;
import org.sjf4j.processor.type.TypeSystem;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;


/**
 * Emits Java source for compiled mapping methods.
 *
 * <p>All structural analysis must already have been completed by
 * {@link MappingCompiler}. This class owns method shape, source reads,
 * target creation and target writes, while value conversion is delegated
 * to {@link ConversionWriter}.</p>
 */
public final class MappingEmitter {

    private final ProcessorContext context;
    private final TypeSystem types;

    private final ConversionWriter conversions;


    public MappingEmitter(
            ProcessorContext context,
            ConversionWriter conversions) {

        this.context = context;
        this.types = context.types;
        this.conversions = conversions;
    }


    /**
     * Adds one compiled mapper method to the generated class.
     */
    public void emit(
            MappingCompiler.CompiledMethod compiled,
            GeneratedClass generated) {

        generated.addMethod(out ->
                emitMethod(
                        out,
                        compiled,
                        generated));
    }


    // -------------------------------------------------------------------------
    // Method
    // -------------------------------------------------------------------------

    private void emitMethod(
            JavaWriter out,
            MappingCompiler.CompiledMethod compiled,
            GeneratedClass generated) {

        MappingPlan plan =
                compiled.plan();

        ExecutableElement method =
                plan.method();

        NameAllocator names =
                names(method);

        out.line(
                "@SuppressWarnings({\"unchecked\", \"rawtypes\"})");
        out.line("@Override");

        out.beginBlock(
                methodHeader(method));

        emitNullRootGuard(
                out,
                plan);

        if (compiled.kind() ==
                MappingCompiler.CompiledMethod.Kind.ROOT) {

            emitRoot(
                    out,
                    names,
                    compiled,
                    generated);

            out.endBlock();
            return;
        }

        emitObject(
                out,
                names,
                compiled,
                generated);

        out.endBlock();
    }


    private void emitRoot(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            GeneratedClass generated) {

        MappingCompiler.Read source =
                compiled.rootSource();

        String sourceValue =
                emitRead(
                        out,
                        names,
                        source,
                        rootKnownNonNull(
                                compiled.plan()));

        if (compiled.plan()
                .create()) {

            String value =
                    conversions.emit(
                            out,
                            names,
                            compiled.plan(),
                            compiled.rootConversion(),
                            sourceValue,
                            source.type(),
                            compiled.plan()
                                    .targetType(),
                            generated);

            out.line(
                    "return " +
                            value +
                            ";");

        } else {
            String target =
                    compiled.plan()
                            .targetParameter()
                            .getSimpleName()
                            .toString();

            out.line(
                    "java.util.Objects.requireNonNull(" +
                            target +
                            ", " +
                            JavaWriter.stringLiteral(
                                    target) +
                            ");");

            NodeKind targetKind =
                    types.nodeKind(
                            compiled.plan()
                                    .targetType());

            if (isArrayLike(targetKind)) {
                if (types.writeElementType(
                        compiled.plan()
                                .targetType()) == null) {

                    error(
                            compiled.plan()
                                    .method(),
                            generated,
                            "root array update target element type is not writable");

                    out.line("return;");
                    return;
                }

                String value =
                        conversions.emit(
                                out,
                                names,
                                compiled.plan(),
                                compiled.rootConversion(),
                                sourceValue,
                                source.type(),
                                compiled.plan()
                                        .targetType(),
                                generated);

                emitArrayContentsUpdate(
                        out,
                        names,
                        target,
                        materialize(
                                out,
                                names,
                                compiled.plan()
                                        .targetType(),
                                value),
                        targetKind,
                        option(
                                compiled.plan(),
                                "arrays",
                                "SET"),
                        compiled.plan(),
                        generated);

            } else if (targetKind ==
                    NodeKind.OBJECT_MAP) {

                conversions.emitMapUpdate(
                        out,
                        names,
                        compiled.plan(),
                        compiled.rootConversion(),
                        sourceValue,
                        source.type(),
                        compiled.plan()
                                .targetType(),
                        target,
                        option(
                                compiled.plan(),
                                "objects",
                                "PUT"),
                        generated);
            }

            out.line("return;");
        }
    }


    private void emitObject(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            GeneratedClass generated) {

        MappingPlan plan =
                compiled.plan();

        String target;

        if (plan.create()) {
            target =
                    emitCreateTarget(
                            out,
                            names,
                            compiled,
                            generated);

        } else {
            target =
                    plan.targetParameter()
                            .getSimpleName()
                            .toString();

            out.line(
                    "java.util.Objects.requireNonNull(" +
                            target +
                            ", " +
                            JavaWriter.stringLiteral(
                                    target) +
                            ");");
        }

        for (MappingCompiler.Assignment assignment :
                compiled.assignments()) {

            boolean presentOnly =
                    emitAutomaticPresenceGuard(
                            out,
                            compiled.plan(),
                            assignment);

            if (emitDirectMapUpdate(
                    out,
                    names,
                    compiled,
                    assignment,
                    target,
                    generated)) {

                if (presentOnly) {
                    out.endBlock();
                }

                continue;
            }

            String value =
                    emitValue(
                            out,
                            names,
                            plan,
                            assignment.value(),
                            generated);

            emitAssignment(
                    out,
                    names,
                    compiled,
                    assignment,
                    target,
                    value,
                    generated);

            if (presentOnly) {
                out.endBlock();
            }
        }

        emitDynamicSources(
                out,
                names,
                compiled,
                target);

        if (plan.create()) {
            out.line(
                    "return " +
                            target +
                            ";");
        }
    }


    private boolean emitAutomaticPresenceGuard(
            JavaWriter out,
            MappingPlan plan,
            MappingCompiler.Assignment assignment) {

        if (assignment.rule() != null ||
                assignment.value().kind() !=
                        MappingCompiler.Value.Kind.READ) {

            return false;
        }

        MappingCompiler.Read read =
                assignment.value()
                        .read();

        if (read.steps()
                .size() != 1 ||
                !(read.steps()
                        .get(0)
                        .segment() instanceof
                        PathSegment.Name)) {

            return false;
        }

        NodeAccess access =
                read.steps()
                        .get(0)
                        .access();

        String source =
                read.root()
                        .getSimpleName()
                        .toString();

        String key =
                JavaWriter.stringLiteral(
                        ((PathSegment.Name) read.steps()
                                .get(0)
                                .segment()).name);

        String present;

        switch (access.kind()) {
            case MAP:
                present = source + ".containsKey(" + key + ")";
                break;

            case JSON_OBJECT:
                present = source + ".containsKey(" + key + ")";
                break;

            case DYNAMIC:
            case EXTERNAL:
                present = "org.sjf4j.Nodes.containsInObject(" +
                        source + ", " + key + ")";
                break;

            default:
                return false;
        }

        if (!rootKnownNonNull(plan)) {
            present = source + " != null && " + present;
        }

        out.beginBlock(
                "if (" +
                        present +
                        ")");

        return true;
    }


    private boolean emitDirectMapUpdate(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            MappingCompiler.Assignment assignment,
            String target,
            GeneratedClass generated) {

        MappingPlan plan =
                compiled.plan();

        MappingCompiler.Target destination =
                assignment.target();

        MappingCompiler.Value mapped =
                assignment.value();

        if (!plan.update() ||
                mapped.kind() !=
                        MappingCompiler.Value.Kind.READ ||
                mapped.conversion().kind() !=
                        ConverterResolver.Conversion.Kind.CONTAINER ||
                types.nodeKind(
                        destination.type()) !=
                        NodeKind.OBJECT_MAP) {

            return false;
        }

        NodeAccess access =
                destination.access();

        if (!access.readable()) {
            error(
                    plan.method(),
                    generated,
                    "object update target '" +
                            destination.name() +
                            "' must be readable");

            return true;
        }

        String source =
                emitRead(
                        out,
                        names,
                        mapped.read(),
                        rootKnownNonNull(plan));

        boolean ignoreNull =
                "IGNORE".equals(
                        option(
                                plan,
                                "nulls",
                                "SET_TO_NULL"));

        MappingPlan.Rule rule =
                assignment.rule();

        String policy =
                rule != null &&
                        rule.objectExplicit()
                        ? rule.objectPolicy()
                        : option(
                        plan,
                        "objects",
                        "PUT");

        if (ignoreNull) {
            out.beginBlock(
                    "if (" +
                            source +
                            " != null)");
        } else {
            out.beginBlock(
                    "if (" +
                            source +
                            " == null)");

            emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    "null");

            out.endBlock();
            out.beginBlock("else");
        }

        String existing =
                names.newName(
                        "existing");

        out.line(
                localType(
                        access.readType()) +
                        " " +
                        existing +
                        " = " +
                        readTargetExpression(
                                target,
                                destination.name(),
                                access) +
                        ";");

        out.beginBlock(
                "if (" +
                        existing +
                        " == null)");

        if (!"PUT_IF_PRESENT".equals(policy)) {
            String value =
                    conversions.emit(
                            out,
                            names,
                            plan,
                            mapped.conversion(),
                            source,
                            mapped.read()
                                    .type(),
                            destination.type(),
                            generated);

            emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    value);
        }

        out.endBlock();
        out.beginBlock("else");

        conversions.emitMapUpdate(
                out,
                names,
                plan,
                mapped.conversion(),
                source,
                mapped.read()
                        .type(),
                destination.type(),
                existing,
                policy,
                generated);

        out.endBlock();
        out.endBlock();

        return true;
    }


    // -------------------------------------------------------------------------
    // JOJO dynamic remainder
    // -------------------------------------------------------------------------

    private void emitDynamicSources(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            String target) {

        for (MappingCompiler.DynamicSource dynamic :
                compiled.dynamicSources()) {

            String source =
                    dynamic.source()
                            .getSimpleName()
                            .toString();

            out.beginBlock(
                    "if (" +
                            source +
                            " != null)");

            switch (dynamic.kind()) {
                case OBJECT_MAP:
                    emitMapDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target);
                    break;

                case OBJECT_JSON_OBJECT:
                    emitJsonObjectDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target,
                            false);
                    break;

                case OBJECT_JOJO:
                    emitJsonObjectDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target,
                            true);
                    break;

                case OBJECT_EXTERNAL:
                case COMPILE_TIME_UNKNOWN:
                    emitRuntimeDynamicSource(
                            out,
                            names,
                            dynamic,
                            source,
                            target);
                    break;

                default:
                    throw new IllegalStateException(
                            "unsupported JOJO dynamic source " +
                                    dynamic.kind());
            }

            out.endBlock();
        }
    }


    private void emitMapDynamicSource(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String source,
            String target) {

        String entry =
                names.newName(
                        "entry");

        out.line(
                "for (java.util.Map.Entry<?, ?> " +
                        entry +
                        " : " +
                        source +
                        ".entrySet()) {");

        out.indent();

        emitDynamicPut(
                out,
                names,
                dynamic,
                target,
                "(String) " + entry + ".getKey()",
                entry + ".getValue()");

        out.dedent();
        out.line("}");
    }


    private void emitJsonObjectDynamicSource(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String source,
            String target,
            boolean dynamicOnly) {

        if (dynamicOnly) {
            out.beginBlock(
                    "if (" +
                            "org.sjf4j.InternalAccess.dynamicProperties(" +
                            source +
                            ") != null)");
        }

        String entry =
                names.newName(
                        "entry");

        String entries =
                dynamicOnly
                        ? "org.sjf4j.InternalAccess.dynamicProperties(" +
                        source +
                        ").entrySet()"
                        : source + ".entrySet()";

        out.line(
                "for (java.util.Map.Entry<String, Object> " +
                        entry +
                        " : " +
                        entries +
                        ") {");

        out.indent();

        emitDynamicPut(
                out,
                names,
                dynamic,
                target,
                entry + ".getKey()",
                entry + ".getValue()");

        out.dedent();
        out.line("}");

        if (dynamicOnly) {
            out.endBlock();
        }
    }


    private void emitRuntimeDynamicSource(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String source,
            String target) {

        String entry =
                names.newName(
                        "entry");

        out.line(
                "for (java.util.Map.Entry<String, Object> " +
                        entry +
                        " : org.sjf4j.Nodes.entrySetInObject(" +
                        source +
                        ")) {");

        out.indent();

        emitDynamicPut(
                out,
                names,
                dynamic,
                target,
                entry + ".getKey()",
                entry + ".getValue()");

        out.dedent();
        out.line("}");
    }


    private void emitDynamicPut(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.DynamicSource dynamic,
            String target,
            String keyExpression,
            String valueExpression) {

        String key =
                names.newName(
                        "key");

        out.line(
                "String " +
                        key +
                        " = " +
                        keyExpression +
                        ";");

        if (!dynamic.excludedNames()
                .isEmpty()) {

            StringBuilder condition =
                    new StringBuilder();

            for (String excluded :
                    dynamic.excludedNames()) {

                if (condition.length() != 0) {
                    condition.append(" || ");
                }

                condition.append(
                                JavaWriter.stringLiteral(
                                        excluded))
                        .append(".equals(")
                        .append(key)
                        .append(')');
            }

            out.line(
                    "if (" +
                            condition +
                            ") continue;");
        }

        out.line(
                target +
                        ".put(" +
                        key +
                        ", " +
                        valueExpression +
                        ");");
    }


    // -------------------------------------------------------------------------
    // Null source roots
    // -------------------------------------------------------------------------

    private void emitNullRootGuard(
            JavaWriter out,
            MappingPlan plan) {

        StringBuilder condition =
                new StringBuilder();

        for (VariableElement source :
                plan.sources()) {

            if (source.asType()
                    .getKind()
                    .isPrimitive()) {

                /*
                 * A primitive source means "all sources are null" can never
                 * become true.
                 */
                return;
            }

            if (condition.length() != 0) {
                condition.append(" && ");
            }

            condition.append(
                            source.getSimpleName())
                    .append(" == null");
        }

        if (condition.length() == 0) {
            return;
        }

        if (plan.create()) {
            out.line(
                    "if (" +
                            condition +
                            ") return null;");

        } else {
            out.line(
                    "if (" +
                            condition +
                            ") return;");
        }
    }


    // -------------------------------------------------------------------------
    // Target creation
    // -------------------------------------------------------------------------

    private String emitCreateTarget(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            GeneratedClass generated) {

        CreatorResolver.Creation creation =
                compiled.creation();

        if (creation == null) {
            throw new IllegalStateException(
                    "create mapping has no creation strategy");
        }

        String target =
                names.newName("target");

        String targetType =
                creation.targetType()
                        .toString();

        switch (creation.kind()) {
            case NO_ARGS:
                out.line(
                        targetType +
                                " " +
                                target +
                                " = new " +
                                targetType +
                                "();");

                return target;

            case FACTORY: {
                ExecutableElement factory =
                        creation.factory();

                String call;

                if (creation.factoryOwner() ==
                        null) {

                    call =
                            factory.getSimpleName() +
                                    "()";

                } else {
                    call =
                            creation.factoryOwner()
                                    .getQualifiedName() +
                                    "." +
                                    factory.getSimpleName() +
                                    "()";
                }

                out.line(
                        targetType +
                                " " +
                                target +
                                " = (" +
                                targetType +
                                ") " +
                                call +
                                ";");

                return target;
            }

            case CONSTRUCTOR: {
                List<MappingCompiler.ConstructorArgument> arguments =
                        compiled.constructorArguments();

                String[] values =
                        new String[arguments.size()];

                for (int i = 0;
                     i < arguments.size();
                     i++) {

                    MappingCompiler.ConstructorArgument argument =
                            arguments.get(i);

                    values[i] =
                            emitValue(
                                    out,
                                    names,
                                    compiled.plan(),
                                    argument.value(),
                                    generated);
                }

                StringBuilder constructor =
                        new StringBuilder();

                constructor.append(
                                targetType)
                        .append(' ')
                        .append(target)
                        .append(" = new ")
                        .append(targetType)
                        .append('(');

                for (int i = 0;
                     i < values.length;
                     i++) {

                    if (i != 0) {
                        constructor.append(", ");
                    }

                    constructor.append(
                            values[i]);
                }

                constructor.append(");");

                out.line(
                        constructor.toString());

                return target;
            }

            default:
                throw new AssertionError(
                        creation.kind());
        }
    }


    // -------------------------------------------------------------------------
    // Value
    // -------------------------------------------------------------------------

    private String emitValue(
            JavaWriter out,
            NameAllocator names,
            MappingPlan plan,
            MappingCompiler.Value value,
            GeneratedClass generated) {

        switch (value.kind()) {
            case READ: {
                MappingCompiler.Read read =
                        value.read();

                String source =
                        emitRead(
                                out,
                                names,
                                read,
                                rootKnownNonNull(plan));

                return conversions.emit(
                        out,
                        names,
                        plan,
                        value.conversion(),
                        source,
                        read.type(),
                        value.type(),
                        generated);
            }

            case COMPUTE:
                return emitCompute(
                        out,
                        names,
                        plan,
                        value);

            default:
                throw new AssertionError(
                        value.kind());
        }
    }


    // -------------------------------------------------------------------------
    // Compute
    // -------------------------------------------------------------------------

    private String emitCompute(
            JavaWriter out,
            NameAllocator names,
            MappingPlan plan,
            MappingCompiler.Value value) {

        List<MappingCompiler.Read> inputs =
                value.inputs();

        String compute =
                value.compute()
                        .trim();

        String targetType =
                localType(
                        value.type());

        if (compute.startsWith("this::")) {
            String method =
                    compute.substring(6)
                            .trim();

            String[] expressions =
                    emitComputeInputs(
                            out,
                            names,
                            inputs,
                            plan);

            String result =
                    names.newName("computed");

            StringBuilder call =
                    new StringBuilder();

            call.append(method)
                    .append('(');

            for (int i = 0;
                 i < expressions.length;
                 i++) {

                if (i != 0) {
                    call.append(", ");
                }

                call.append(
                        expressions[i]);
            }

            call.append(')');

            out.line(
                    targetType +
                            " " +
                            result +
                            " = " +
                            call +
                            ";");

            return result;
        }

        int arrow =
                compute.indexOf("->");

        if (arrow < 0) {
            throw new IllegalStateException(
                    "invalid compute expression: " +
                            compute);
        }

        String parameters =
                compute.substring(
                                0,
                                arrow)
                        .trim();

        String expression =
                compute.substring(
                                arrow + 2)
                        .trim();

        if (parameters.startsWith("(") &&
                parameters.endsWith(")")) {

            parameters =
                    parameters.substring(
                            1,
                            parameters.length() - 1);
        }

        String[] parameterNames =
                parameters.isEmpty()
                        ? new String[0]
                        : parameters.split("\\s*,\\s*");

        if (parameterNames.length !=
                inputs.size()) {

            throw new IllegalStateException(
                    "compute parameter count does not match sources");
        }

        for (String parameterName :
                parameterNames) {

            names.reserve(
                    parameterName.trim());
        }

        String result =
                names.newName("computed");

        out.line(
                targetType +
                        " " +
                        result +
                        " = null;");
        out.line("{");
        out.indent();

        String[] expressions =
                emitComputeInputs(
                        out,
                        names,
                        inputs,
                        plan);

        for (int i = 0;
             i < parameterNames.length;
             i++) {

            String parameter =
                    parameterNames[i]
                            .trim();

            out.line(
                    localType(
                            inputs.get(i)
                                    .type()) +
                            " " +
                            parameter +
                            " = " +
                            expressions[i] +
                            ";");
        }

        out.line(
                result +
                        " = " +
                        expression +
                        ";");

        out.dedent();
        out.line("}");

        return result;
    }


    private String[] emitComputeInputs(
            JavaWriter out,
            NameAllocator names,
            List<MappingCompiler.Read> inputs,
            MappingPlan plan) {

        String[] expressions =
                new String[inputs.size()];

        for (int i = 0;
             i < inputs.size();
             i++) {

            expressions[i] =
                    emitRead(
                            out,
                            names,
                            inputs.get(i),
                            rootKnownNonNull(plan));
        }

        return expressions;
    }


    // -------------------------------------------------------------------------
    // Source reads
    // -------------------------------------------------------------------------

    private String emitRead(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Read read,
            boolean rootKnownNonNull) {

        String current =
                read.root()
                        .getSimpleName()
                        .toString();

        if (read.rootValue()) {
            return current;
        }

        TypeMirror currentType =
                read.root()
                        .asType();

        boolean first = true;

        for (MappingCompiler.ReadStep step :
                read.steps()) {

            NodeAccess access =
                    step.access();

            PathSegment segment =
                    step.segment();

            TypeMirror valueType =
                    access.readType();

            String value =
                    names.newName("sourceValue");

            boolean parentPrimitive =
                    currentType.getKind()
                            .isPrimitive();

            boolean guardParent =
                    !parentPrimitive &&
                            !(first &&
                                    rootKnownNonNull);

            if (guardParent) {
                /*
                 * Box primitive values so a null parent can propagate as
                 * null.
                 */
                out.line(
                        localType(valueType) +
                                " " +
                                value +
                                " = null;");

                out.beginBlock(
                        "if (" +
                                current +
                                " != null)");

            } else if (segment instanceof
                    PathSegment.Index) {

                out.line(
                        localType(valueType) +
                                " " +
                                value +
                                " = null;");
            }

            if (segment instanceof
                    PathSegment.Index) {

                emitIndexedRead(
                        out,
                        names,
                        current,
                        value,
                        valueType,
                        access,
                        ((PathSegment.Index) segment)
                                .index);

            } else {
                String expression =
                        readExpression(
                                current,
                                segment,
                                access,
                                valueType);

                if (guardParent) {
                    out.line(
                            value +
                                    " = " +
                                    expression +
                                    ";");
                } else {
                    out.line(
                            localType(valueType) +
                                    " " +
                                    value +
                                    " = " +
                                    expression +
                                    ";");
                }
            }

            if (guardParent) {
                out.endBlock();
            }

            current =
                    value;

            currentType =
                    valueType;

            first = false;
        }

        return current;
    }


    private boolean rootKnownNonNull(
            MappingPlan plan) {

        return plan.sources()
                .size() == 1;
    }


    private void emitIndexedRead(
            JavaWriter out,
            NameAllocator names,
            String owner,
            String target,
            TypeMirror valueType,
            NodeAccess access,
            int rawIndex) {

        String size =
                sizeExpression(
                        owner,
                        access.kind());

        if (size == null) {
            out.line(
                    target +
                            " = " +
                            readExpression(
                                    owner,
                                    new PathSegment.Index(
                                            PathSegment.Root.INSTANCE,
                                            rawIndex),
                                    access,
                                    valueType) +
                            ";");

            return;
        }

        String index =
                names.newName("index");

        out.line(
                "int " +
                        index +
                        " = " +
                        rawIndex +
                        ";");

        out.line(
                "if (" +
                        index +
                        " < 0) " +
                        index +
                        " += " +
                        size +
                        ";");

        out.beginBlock(
                "if (" +
                        index +
                        " >= 0 && " +
                        index +
                        " < " +
                        size +
                        ")");

        out.line(
                target +
                        " = " +
                        indexedReadExpression(
                                owner,
                                index,
                                access,
                                valueType) +
                        ";");

        out.endBlock();
    }


    private String readExpression(
            String owner,
            PathSegment segment,
            NodeAccess access,
            TypeMirror valueType) {

        if (segment instanceof
                PathSegment.Name) {

            String name =
                    ((PathSegment.Name) segment)
                            .name;

            String key =
                    JavaWriter.stringLiteral(
                            name);

            switch (access.kind()) {
                case PROPERTY: {
                    PropertyAccess read =
                            access.property()
                                    .read();

                    return read.isMethod()
                            ? owner +
                            "." +
                            read.memberName() +
                            "()"
                            : owner +
                            "." +
                            read.memberName();
                }

                case MAP:
                    return owner +
                            ".get(" +
                            key +
                            ")";

                case JSON_OBJECT:
                    return cast(
                            valueType,
                            owner +
                                    ".getNode(" +
                                    key +
                                    ")");

                case DYNAMIC:
                case EXTERNAL:
                    return cast(
                            valueType,
                            "org.sjf4j.Nodes.getInObject(" +
                                    owner +
                                    ", " +
                                    key +
                                    ")");

                default:
                    throw new IllegalStateException(
                            "unsupported name read: " +
                                    access.kind());
            }
        }

        if (segment instanceof
                PathSegment.Index) {

            return indexedReadExpression(
                    owner,
                    Integer.toString(
                            ((PathSegment.Index) segment)
                                    .index),
                    access,
                    valueType);
        }

        throw new IllegalStateException(
                "unsupported source segment: " +
                        segment);
    }


    private String indexedReadExpression(
            String owner,
            String index,
            NodeAccess access,
            TypeMirror valueType) {

        switch (access.kind()) {
            case ARRAY:
                return owner +
                        "[" +
                        index +
                        "]";

            case LIST:
                return owner +
                        ".get(" +
                        index +
                        ")";

            case JSON_ARRAY:
                return cast(
                        valueType,
                        owner +
                                ".getNode(" +
                                index +
                                ")");

            case DYNAMIC:
            case EXTERNAL:
                return cast(
                        valueType,
                        "org.sjf4j.Nodes.getInArray(" +
                                owner +
                                ", " +
                                index +
                                ")");

            default:
                throw new IllegalStateException(
                        "unsupported index read: " +
                                access.kind());
        }
    }


    // -------------------------------------------------------------------------
    // Assignment
    // -------------------------------------------------------------------------

    private void emitAssignment(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            MappingCompiler.Assignment assignment,
            String target,
            String value,
            GeneratedClass generated) {

        MappingCompiler.Target destination =
                assignment.target();

        if (destination.kind() ==
                MappingCompiler.Target.Kind.PATH) {

            emitPathWrite(
                    out,
                    names,
                    destination,
                    target,
                    value);

            return;
        }

        emitPropertyAssignment(
                out,
                names,
                compiled,
                assignment,
                target,
                value,
                generated);
    }


    private void emitPropertyAssignment(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.CompiledMethod compiled,
            MappingCompiler.Assignment assignment,
            String target,
            String value,
            GeneratedClass generated) {

        MappingCompiler.Target destination =
                assignment.target();

        NodeAccess access =
                destination.access();

        TypeMirror targetType =
                destination.type();

        MappingPlan plan =
                compiled.plan();

        MappingPlan.Rule rule =
                assignment.rule();

        String nullPolicy =
                option(
                        plan,
                        "nulls",
                        "SET_TO_NULL");

        boolean ignoreNull =
                "IGNORE".equals(
                        nullPolicy);

        NodeKind kind =
                types.nodeKind(
                        targetType);

        if (plan.update() &&
                isArrayLike(kind)) {

            String arrayPolicy =
                    rule != null &&
                            rule.arrayExplicit()
                            ? rule.arrayPolicy()
                             : option(
                             plan,
                             "arrays",
                            "SET");

            emitArrayUpdate(
                    out,
                    names,
                    destination,
                    target,
                    value,
                    arrayPolicy,
                    ignoreNull,
                    generated,
                    plan);

            return;
        }

        if (plan.update() &&
                isMapLike(kind)) {

            String objectPolicy =
                    rule != null &&
                            rule.objectExplicit()
                            ? rule.objectPolicy()
                            : option(
                            plan,
                            "objects",
                            "PUT");

            if (kind != NodeKind.OBJECT_MAP &&
                    !"PUT".equals(objectPolicy)) {

                error(
                        plan.method(),
                        generated,
                        "ObjectPolicy." +
                                objectPolicy +
                                " requires a Map target");

                return;
            }

            emitObjectUpdate(
                    out,
                    names,
                    destination,
                    target,
                    value,
                    objectPolicy,
                    ignoreNull,
                    generated,
                    plan);

            return;
        }

        if (ignoreNull ||
                targetType
                        .getKind()
                        .isPrimitive()) {

            out.beginBlock(
                    "if (" +
                            value +
                            " != null)");

            emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    value);

            out.endBlock();

        } else {
            emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    value);
        }
    }


    // -------------------------------------------------------------------------
    // Array update policy
    // -------------------------------------------------------------------------

    private void emitArrayUpdate(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Target destination,
            String target,
            String value,
            String policy,
            boolean ignoreNull,
            GeneratedClass generated,
            MappingPlan plan) {

        NodeAccess access =
                destination.access();

        if (!access.readable()) {
            error(
                    plan.method(),
                    generated,
                    "array update target '" +
                            destination.name() +
                            "' must be readable");

            return;
        }

        NodeKind existingKind =
                types.nodeKind(
                        access.readType());

        if (existingKind ==
                NodeKind.ARRAY_SET &&
                "SET".equals(policy)) {

            error(
                    plan.method(),
                    generated,
                    "ArrayPolicy.SET requires an indexed target");

            return;
        }

        if (access.readType()
                .getKind() ==
                TypeKind.ARRAY &&
                "ADD".equals(policy)) {

            error(
                    plan.method(),
                    generated,
                    "ArrayPolicy." +
                            policy +
                            " cannot update a Java array in place");

            return;
        }

        if (types.writeElementType(
                access.readType()) == null) {

            error(
                    plan.method(),
                    generated,
                    "array update target '" +
                            destination.name() +
                            "' element type is not writable");

            return;
        }

        value = materialize(
                out,
                names,
                destination.type(),
                value);

        if (ignoreNull) {
            out.beginBlock(
                    "if (" +
                            value +
                            " != null)");
        } else {
            out.beginBlock(
                    "if (" +
                            value +
                            " == null)");

            emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    "null");

            out.endBlock();

            out.beginBlock("else");
        }

        String existing =
                names.newName(
                        "existing");

        out.line(
                localType(
                        access.readType()) +
                        " " +
                        existing +
                        " = " +
                        readTargetExpression(
                                target,
                                destination.name(),
                                access) +
                        ";");

        out.beginBlock(
                "if (" +
                        existing +
                        " == null)");

        emitNodeWrite(
                out,
                target,
                destination.name(),
                null,
                access,
                value);

        out.endBlock();

        out.beginBlock("else");

        emitArrayContentsUpdate(
                out,
                names,
                existing,
                value,
                existingKind,
                policy,
                plan,
                generated);

        out.endBlock();

        out.endBlock();
    }


    private void emitArrayContentsUpdate(
            JavaWriter out,
            NameAllocator names,
            String target,
            String value,
            NodeKind targetKind,
            String policy,
            MappingPlan plan,
            GeneratedClass generated) {

        if (targetKind ==
                NodeKind.ARRAY_SET &&
                "SET".equals(policy)) {

            error(
                    plan.method(),
                    generated,
                    "ArrayPolicy.SET requires an indexed target");

        return;
    }


        if (targetKind ==
                NodeKind.ARRAY_ARRAY &&
                "ADD".equals(policy)) {

            error(
                    plan.method(),
                    generated,
                    "ArrayPolicy.ADD cannot grow a Java array");

            return;
        }

        if ("ADD".equals(policy)) {
            out.line(
                    target +
                            ".addAll(" +
                            value +
                            ");");

            return;
        }

        if (targetKind ==
                NodeKind.ARRAY_ARRAY) {

            out.beginBlock(
                    "if (" +
                            value +
                            ".length > " +
                            target +
                            ".length)");
            out.line(
                    "throw new org.sjf4j.exception.BindingException(" +
                            JavaWriter.stringLiteral(
                                    "ArrayPolicy.SET source length exceeds target array length") +
                            ");");
            out.endBlock();

            String index =
                    names.newName("i");

            out.line(
                    "for (int " +
                            index +
                            " = 0; " +
                            index +
                            " < " +
                            value +
                            ".length; " +
                            index +
                            "++) {");
            out.indent();
            out.line(
                    target +
                            "[" +
                            index +
                            "] = " +
                            value +
                            "[" +
                            index +
                            "];");
            out.dedent();
            out.line("}");

            return;
        }

        String index =
                names.newName("i");

        out.line(
                "for (int " +
                        index +
                        " = 0; " +
                        index +
                        " < " +
                        value +
                        ".size(); " +
                        index +
                        "++) {");
        out.indent();
        out.beginBlock(
                "if (" +
                        index +
                        " < " +
                        target +
                        ".size())");

        if (targetKind ==
                NodeKind.ARRAY_LIST) {
            out.line(
                    target +
                            ".set(" +
                            index +
                            ", " +
                            value +
                            ".get(" +
                            index +
                            "));");
        } else {
            out.line(
                    target +
                            ".set(" +
                            index +
                            ", " +
                            value +
                            ".getNode(" +
                            index +
                            "));");
        }

        out.endBlock();
        out.beginBlock("else");

        if (targetKind ==
                NodeKind.ARRAY_LIST) {
            out.line(
                    target +
                            ".add(" +
                            value +
                            ".get(" +
                            index +
                            "));");
        } else {
            out.line(
                    target +
                            ".add(" +
                            value +
                            ".getNode(" +
                            index +
                            "));");
        }

        out.endBlock();
        out.dedent();
        out.line("}");
    }


    private String materialize(
            JavaWriter out,
            NameAllocator names,
            TypeMirror type,
            String value) {

        String result =
                names.newName(
                        "converted");

        out.line(
                localType(type) +
                        " " +
                        result +
                        " = " +
                        value +
                        ";");

        return result;
    }


    // -------------------------------------------------------------------------
    // Object update policy
    // -------------------------------------------------------------------------

    private void emitObjectUpdate(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Target destination,
            String target,
            String value,
            String policy,
            boolean ignoreNull,
            GeneratedClass generated,
            MappingPlan plan) {

        NodeAccess access =
                destination.access();

        if (!access.readable()) {
            error(
                    plan.method(),
                    generated,
                    "object update target '" +
                            destination.name() +
                            "' must be readable");

            return;
        }

        if (ignoreNull) {
            out.beginBlock(
                    "if (" +
                            value +
                            " != null)");
        } else {
            out.beginBlock(
                    "if (" +
                            value +
                            " == null)");

            emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    "null");

            out.endBlock();

            out.beginBlock("else");
        }

        String existing =
                names.newName(
                        "existing");

        out.line(
                localType(
                        access.readType()) +
                        " " +
                        existing +
                        " = " +
                        readTargetExpression(
                                target,
                                destination.name(),
                                access) +
                        ";");

        out.beginBlock(
                "if (" +
                        existing +
                        " == null)");

        if (!"PUT_IF_PRESENT".equals(policy)) {
            emitNodeWrite(
                    out,
                    target,
                    destination.name(),
                    null,
                    access,
                    value);
        }

        out.endBlock();

        out.beginBlock("else");

        if ("PUT_IF_ABSENT".equals(
                policy)) {

            String entry =
                    names.newName("entry");

            out.line(
                    "for (Object " +
                            entry +
                            " : ((java.util.Map) " +
                            value +
                            ").entrySet()) {");

            out.indent();

            out.beginBlock(
                    "if (!((java.util.Map) " +
                            existing +
                            ").containsKey(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) || " +
                            "((java.util.Map) " +
                            existing +
                            ").get(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) == null)");

            out.line(
                    "((java.util.Map) " +
                            existing +
                            ").put(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey(), " +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getValue());");

            out.endBlock();

            out.dedent();
            out.line("}");

        } else if ("PUT_IF_PRESENT".equals(
                policy)) {

            String entry =
                    names.newName("entry");

            out.line(
                    "for (Object " +
                            entry +
                            " : ((java.util.Map) " +
                            value +
                            ").entrySet()) {");

            out.indent();

            out.beginBlock(
                    "if (((java.util.Map) " +
                            existing +
                            ").containsKey(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) && " +
                            "((java.util.Map) " +
                            existing +
                            ").get(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey()) != null)");

            out.line(
                    "((java.util.Map) " +
                            existing +
                            ").put(" +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getKey(), " +
                            "((java.util.Map.Entry) " +
                            entry +
                            ").getValue());");

            out.endBlock();

            out.dedent();
            out.line("}");

        } else {
            /*
             * PUT
             */
            out.line(
                    existing +
                            ".putAll(" +
                            value +
                            ");");
        }

        out.endBlock();

        out.endBlock();
    }


    // -------------------------------------------------------------------------
    // Target path
    // -------------------------------------------------------------------------

    private void emitPathWrite(
            JavaWriter out,
            NameAllocator names,
            MappingCompiler.Target target,
            String root,
            String value) {

        List<MappingCompiler.TargetStep> steps =
                target.steps();

        String current =
                root;

        TypeMirror currentType =
                steps.get(0)
                        .access()
                        .ownerType();

        int opened =
                0;

        for (int i = 0;
             i < steps.size() - 1;
             i++) {

            MappingCompiler.TargetStep step =
                    steps.get(i);

            NodeAccess access =
                    step.access();

            PathSegment segment =
                    step.segment();

            String child =
                    names.newName(
                            "parent");

            String index =
                    null;

            if (segment instanceof
                    PathSegment.Index) {

                index =
                        emitIndex(
                                out,
                                names,
                                current,
                                access,
                                ((PathSegment.Index) segment)
                                        .index,
                                target.mode());

                if (index == null) {
                    return;
                }
            }

            out.line(
                    localType(
                            access.readType()) +
                            " " +
                            child +
                            " = " +
                            readTargetExpression(
                                    current,
                                    segment,
                                    index,
                                    access) +
                            ";");

            switch (target.mode()) {
                case STRICT:
                    out.line(
                            "java.util.Objects.requireNonNull(" +
                                    child +
                                    ", " +
                                    JavaWriter.stringLiteral(
                                            "missing target path parent") +
                                    ");");
                    break;

                case IF_PARENT_PRESENT:
                    out.beginBlock(
                            "if (" +
                                    child +
                                    " != null)");

                    opened++;
                    break;

                case ENSURE:
                    out.beginBlock(
                            "if (" +
                                    child +
                                    " == null)");

                    String created =
                            ensureExpression(
                                    access.writeType(),
                                    steps.get(i + 1)
                                            .segment());

                    out.line(
                            child +
                                    " = " +
                                    created +
                                    ";");

                    emitNodeWrite(
                            out,
                            current,
                            segment,
                            index,
                            access,
                            child);

                    out.endBlock();
                    break;

                default:
                    throw new AssertionError(
                            target.mode());
            }

            current =
                    child;

            currentType =
                    access.readType();
        }

        MappingCompiler.TargetStep tail =
                steps.get(
                        steps.size() - 1);

        PathSegment segment =
                tail.segment();

        String index =
                null;

        if (segment instanceof
                PathSegment.Index) {

            index =
                    emitFinalIndex(
                            out,
                            names,
                            current,
                            tail.access(),
                            ((PathSegment.Index) segment)
                                    .index,
                            target.mode());
        }

        emitNodeWrite(
                out,
                current,
                segment,
                index,
                tail.access(),
                value);

        while (opened-- > 0) {
            out.endBlock();
        }
    }


    private String emitIndex(
            JavaWriter out,
            NameAllocator names,
            String owner,
            NodeAccess access,
            int rawIndex,
            MappingPlan.WriteMode mode) {

        String size =
                sizeExpression(
                        owner,
                        access.kind());

        if (size == null) {
            return Integer.toString(
                    rawIndex);
        }

        String index =
                names.newName("index");

        out.line(
                "int " +
                        index +
                        " = " +
                        rawIndex +
                        ";");

        out.line(
                "if (" +
                        index +
                        " < 0) " +
                        index +
                        " += " +
                        size +
                        ";");

        if (mode ==
                MappingPlan.WriteMode.IF_PARENT_PRESENT) {

            out.beginBlock(
                    "if (" +
                            index +
                            " >= 0 && " +
                            index +
                            " < " +
                            size +
                            ")");

            /*
             * The caller opens a second parent-present block. Keeping the
             * index scope open would complicate close tracking, so indexed
             * IF_PARENT traversal is emitted by a normal guarded expression.
             */
            out.endBlock();

        } else {
            out.beginBlock(
                    "if (" +
                            index +
                            " < 0 || " +
                            index +
                            " >= " +
                            size +
                            ")");

            out.line(
                    "throw new IndexOutOfBoundsException(\"target path index: \" + " +
                            index +
                            ");");

            out.endBlock();
        }

        return index;
    }


    private String emitFinalIndex(
            JavaWriter out,
            NameAllocator names,
            String owner,
            NodeAccess access,
            int rawIndex,
            MappingPlan.WriteMode mode) {

        String size =
                sizeExpression(
                        owner,
                        access.kind());

        if (size == null) {
            return Integer.toString(
                    rawIndex);
        }

        String index =
                names.newName("index");

        out.line(
                "int " +
                        index +
                        " = " +
                        rawIndex +
                        ";");

        out.line(
                "if (" +
                        index +
                        " < 0) " +
                        index +
                        " += " +
                        size +
                        ";");

        return index;
    }


    // -------------------------------------------------------------------------
    // Node writes
    // -------------------------------------------------------------------------

    private void emitNodeWrite(
            JavaWriter out,
            String owner,
            String name,
            String index,
            NodeAccess access,
            String value) {

        if (name != null) {
            emitNodeWrite(
                    out,
                    owner,
                    new PathSegment.Name(
                            PathSegment.Root.INSTANCE,
                            name),
                    null,
                    access,
                    value);

            return;
        }

        throw new IllegalArgumentException(
                "name is required");
    }


    private void emitNodeWrite(
            JavaWriter out,
            String owner,
            PathSegment segment,
            String index,
            NodeAccess access,
            String value) {

        if (segment instanceof
                PathSegment.Name) {

            String name =
                    ((PathSegment.Name) segment)
                            .name;

            String key =
                    JavaWriter.stringLiteral(
                            name);

            switch (access.kind()) {
                case PROPERTY: {
                    PropertyAccess write =
                            access.property()
                                    .write();

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

                    return;
                }

                case MAP:
                case JSON_OBJECT:
                    out.line(
                            owner +
                                    ".put(" +
                                    key +
                                    ", " +
                                    value +
                                    ");");
                    return;

                case DYNAMIC:
                case EXTERNAL:
                    out.line(
                            "org.sjf4j.Nodes.putInObject(" +
                                    owner +
                                    ", " +
                                    key +
                                    ", " +
                                    value +
                                    ");");
                    return;

                default:
                    throw new IllegalStateException(
                            "unsupported name write: " +
                                    access.kind());
            }
        }

        if (segment instanceof
                PathSegment.Append) {

            switch (access.kind()) {
                case LIST:
                case JSON_ARRAY:
                    out.line(
                            owner +
                                    ".add(" +
                                    value +
                                    ");");
                    return;

                case DYNAMIC:
                case EXTERNAL:
                    out.line(
                            "org.sjf4j.Nodes.addInArray(" +
                                    owner +
                                    ", " +
                                    value +
                                    ");");
                    return;

                default:
                    throw new IllegalStateException(
                            "unsupported append write: " +
                                    access.kind());
            }
        }

        if (segment instanceof
                PathSegment.Index) {

            String actualIndex =
                    index != null
                            ? index
                            : Integer.toString(
                            ((PathSegment.Index) segment)
                                    .index);

            switch (access.kind()) {
                case ARRAY:
                    out.line(
                            owner +
                                    "[" +
                                    actualIndex +
                                    "] = " +
                                    value +
                                    ";");
                    return;

                case LIST:
                    out.beginBlock(
                            "if (" +
                                    actualIndex +
                                    " == " +
                                    owner +
                                    ".size())");

                    out.line(
                            owner +
                                    ".add(" +
                                    value +
                                    ");");

                    out.endBlock();
                    out.beginBlock("else");

                    out.line(
                            owner +
                                    ".set(" +
                                    actualIndex +
                                    ", " +
                                    value +
                                    ");");

                    out.endBlock();
                    return;

                case JSON_ARRAY:
                    out.beginBlock(
                            "if (" +
                                    actualIndex +
                                    " == " +
                                    owner +
                                    ".size())");

                    out.line(
                            owner +
                                    ".add(" +
                                    value +
                                    ");");

                    out.endBlock();
                    out.beginBlock("else");

                    out.line(
                            owner +
                                    ".set(" +
                                    actualIndex +
                                    ", " +
                                    value +
                                    ");");

                    out.endBlock();
                    return;

                case DYNAMIC:
                case EXTERNAL:
                    out.line(
                            "org.sjf4j.Nodes.putInArray(" +
                                    owner +
                                    ", " +
                                    actualIndex +
                                    ", " +
                                    value +
                                    ");");
                    return;

                default:
                    throw new IllegalStateException(
                            "unsupported index write: " +
                                    access.kind());
            }
        }

        throw new IllegalStateException(
                "unsupported target segment: " +
                        segment);
    }


    // -------------------------------------------------------------------------
    // Target reads
    // -------------------------------------------------------------------------

    private String readTargetExpression(
            String owner,
            String name,
            NodeAccess access) {

        return readTargetExpression(
                owner,
                new PathSegment.Name(
                        PathSegment.Root.INSTANCE,
                        name),
                null,
                access);
    }


    private String readTargetExpression(
            String owner,
            PathSegment segment,
            String index,
            NodeAccess access) {

        if (segment instanceof
                PathSegment.Name) {

            String name =
                    ((PathSegment.Name) segment)
                            .name;

            String key =
                    JavaWriter.stringLiteral(
                            name);

            switch (access.kind()) {
                case PROPERTY: {
                    PropertyAccess read =
                            access.property()
                                    .read();

                    return read.isMethod()
                            ? owner +
                            "." +
                            read.memberName() +
                            "()"
                            : owner +
                            "." +
                            read.memberName();
                }

                case MAP:
                    return owner +
                            ".get(" +
                            key +
                            ")";

                case JSON_OBJECT:
                    return cast(
                            access.readType(),
                            owner +
                                    ".getNode(" +
                                    key +
                                    ")");

                case DYNAMIC:
                case EXTERNAL:
                    return cast(
                            access.readType(),
                            "org.sjf4j.Nodes.getInObject(" +
                                    owner +
                                    ", " +
                                    key +
                                    ")");

                default:
                    throw new IllegalStateException(
                            "unsupported target name read: " +
                                    access.kind());
            }
        }

        if (segment instanceof
                PathSegment.Index) {

            String actualIndex =
                    index != null
                            ? index
                            : Integer.toString(
                            ((PathSegment.Index) segment)
                                    .index);

            return indexedReadExpression(
                    owner,
                    actualIndex,
                    access,
                    access.readType());
        }

        throw new IllegalStateException(
                "unsupported target read: " +
                        segment);
    }


    // -------------------------------------------------------------------------
    // Ensure
    // -------------------------------------------------------------------------

    private String ensureExpression(
            TypeMirror type,
            PathSegment next) {

        type =
                types.concrete(type);

        if (type == null ||
                types.isObject(type)) {

            if (next instanceof
                    PathSegment.Index ||
                    next instanceof
                            PathSegment.Append) {

                return "new java.util.ArrayList<>()";
            }

            return "new java.util.LinkedHashMap<>()";
        }

        NodeKind kind =
                types.nodeKind(type);

        switch (kind) {
            case OBJECT_MAP:
                if (types.isSameErasure(
                        type,
                        types.mapType())) {

                    return "new java.util.LinkedHashMap<>()";
                }

                return "new " +
                        type +
                        "()";

            case ARRAY_LIST:
                if (types.isSameErasure(
                        type,
                        types.listType())) {

                    return "new java.util.ArrayList<>()";
                }

                return "new " +
                        type +
                        "()";

            case OBJECT_JSON_OBJECT:
                return "new org.sjf4j.JsonObject()";

            case ARRAY_JSON_ARRAY:
                return "new org.sjf4j.JsonArray()";

            case OBJECT_POJO:
            case OBJECT_JOJO:
            case ARRAY_JAJO:
                return "new " +
                        type +
                        "()";

            default:
                throw new IllegalStateException(
                        "cannot ensure target type " +
                                type);
        }
    }


    // -------------------------------------------------------------------------
    // Options
    // -------------------------------------------------------------------------

    private String option(
            MappingPlan plan,
            String name,
            String defaultValue) {

        AnnotationMirror options =
                plan.options();

        if (options == null) {
            return defaultValue;
        }

        Map<? extends ExecutableElement,
                ? extends AnnotationValue> values =
                context.elements
                        .getElementValuesWithDefaults(
                                options);

        for (Map.Entry<? extends ExecutableElement,
                ? extends AnnotationValue> entry :
                values.entrySet()) {

            if (!entry.getKey()
                    .getSimpleName()
                    .contentEquals(name)) {

                continue;
            }

            Object raw =
                    entry.getValue()
                            .getValue();

            if (raw instanceof
                    VariableElement) {

                return ((VariableElement) raw)
                        .getSimpleName()
                        .toString();
            }

            return raw == null
                    ? defaultValue
                    : raw.toString();
        }

        return defaultValue;
    }


    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String methodHeader(
            ExecutableElement method) {

        StringBuilder result =
                new StringBuilder();

        result.append("public ")
                .append(method.getReturnType())
                .append(' ')
                .append(method.getSimpleName())
                .append('(');

        List<? extends VariableElement> parameters =
                method.getParameters();

        for (int i = 0;
             i < parameters.size();
             i++) {

            if (i != 0) {
                result.append(", ");
            }

            VariableElement parameter =
                    parameters.get(i);

            result.append(
                            parameter.asType())
                    .append(' ')
                    .append(
                            parameter.getSimpleName());
        }

        result.append(')');

        List<? extends TypeMirror> thrown =
                method.getThrownTypes();

        if (!thrown.isEmpty()) {
            result.append(" throws ");

            for (int i = 0;
                 i < thrown.size();
                 i++) {

                if (i != 0) {
                    result.append(", ");
                }

                result.append(
                        thrown.get(i));
            }
        }

        return result.toString();
    }


    private NameAllocator names(
            ExecutableElement method) {

        NameAllocator names =
                new NameAllocator();

        for (VariableElement parameter :
                method.getParameters()) {

            names.reserve(
                    parameter.getSimpleName()
                            .toString());
        }

        return names;
    }


    private String localType(
            TypeMirror type) {

        return types.boxed(
                        types.concrete(type))
                .toString();
    }


    private String cast(
            TypeMirror type,
            String expression) {

        TypeMirror concrete =
                types.concrete(type);

        if (types.isObject(concrete)) {
            return expression;
        }

        return "(" +
                localType(concrete) +
                ") " +
                expression;
    }


    private String sizeExpression(
            String owner,
            NodeAccess.Kind kind) {

        switch (kind) {
            case ARRAY:
                return owner +
                        ".length";

            case LIST:
            case JSON_ARRAY:
                return owner +
                        ".size()";

            default:
                return null;
        }
    }


    private boolean isArrayLike(
            NodeKind kind) {

        switch (kind) {
            case ARRAY_ARRAY:
            case ARRAY_LIST:
            case ARRAY_SET:
            case ARRAY_JSON_ARRAY:
            case ARRAY_JAJO:
                return true;

            default:
                return false;
        }
    }


    private boolean isMapLike(
            NodeKind kind) {

        switch (kind) {
            case OBJECT_MAP:
            case OBJECT_JSON_OBJECT:
            case OBJECT_JOJO:
                return true;

            default:
                return false;
        }
    }


    private void error(
            ExecutableElement method,
            GeneratedClass generated,
            String message) {

        generated.invalidate();

        context.error(
                method,
                generated.originName() +
                        ": " +
                        message);
    }


    // -------------------------------------------------------------------------
    // Conversion boundary
    // -------------------------------------------------------------------------

    /**
     * Emits conversion of one already-read source value.
     *
     * <p>The implementation may emit local variables, loops or structural
     * mapping code and returns the expression/local name containing the final
     * converted value.</p>
     */
    public interface ConversionWriter {

        String emit(
                JavaWriter out,
                NameAllocator names,
                MappingPlan plan,
                ConverterResolver.Conversion conversion,
                String source,
                TypeMirror sourceType,
                TypeMirror targetType,
                GeneratedClass generated);

        void emitMapUpdate(
                JavaWriter out,
                NameAllocator names,
                MappingPlan plan,
                ConverterResolver.Conversion conversion,
                String source,
                TypeMirror sourceType,
                TypeMirror targetType,
                String target,
                String policy,
                GeneratedClass generated);
    }
}
