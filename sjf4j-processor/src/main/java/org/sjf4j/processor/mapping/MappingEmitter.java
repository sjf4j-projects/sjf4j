package org.sjf4j.processor.mapping;

import org.sjf4j.NodeKind;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaExpressions;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.MethodSignature;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.type.TypeSystem;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/** Coordinates compiled mapping method emission, target creation and value assignment. */
public final class MappingEmitter {

    private final CompilationContext context;
    private final TypeSystem types;
    private final ConversionWriter conversions;
    private final MappingReadEmitter sourceReads;
    private final MappingValueEmitter valueEmitter;
    private final MappingUpdateEmitter updates;

    public MappingEmitter(CompilationContext context, ConversionWriter conversions) {
        this.context = context;
        this.types = context.types;
        this.conversions = conversions;
        this.sourceReads = new MappingReadEmitter(context);
        MappingWriteEmitter writes = new MappingWriteEmitter(context.types);
        this.valueEmitter = new MappingValueEmitter(context.types, sourceReads, conversions);
        this.updates = new MappingUpdateEmitter(context, sourceReads, writes, conversions);
    }

    /**
     * Adds one compiled mapper method to the generated class.
     */
    public void emit(MappingCompiler.CompiledMethod compiled, GeneratedClass generated) {
        generated.addMethod(out -> emitMethod(out, compiled, generated));
    }

    private void emitMethod(JavaWriter out, MappingCompiler.CompiledMethod compiled,
                            GeneratedClass generated) {
        MappingPlan plan = compiled.plan();
        ExecutableElement method = plan.method();
        NameAllocator names = generated.names().locals(method);

        out.line("@SuppressWarnings({\"unchecked\", \"rawtypes\"})");
        out.line("@Override");
        out.beginBlock(MethodSignature.override(plan.method(), plan.methodType(), true));
        emitNullRootGuard(out, plan);

        if (compiled.kind() == MappingCompiler.CompiledMethod.Kind.ROOT) {
            emitRoot(out, names, compiled, generated);
            out.endBlock();
            return;
        }

        emitObject(out, names, compiled, generated);
        out.endBlock();
    }

    private void emitRoot(JavaWriter out, NameAllocator names, MappingCompiler.CompiledMethod compiled,
                          GeneratedClass generated) {
        MappingCompiler.Read source = compiled.rootSource();
        String sourceValue = sourceReads.emitRead(out, names, source, MappingEmissionSupport.rootKnownNonNull(compiled.plan()));
        boolean sourceKnownNonNull = sourceReads.readKnownNonNull(compiled.plan(), source);

        if (compiled.plan().create()) {

            String value = conversions.emit(out, names, compiled.plan(), compiled.rootConversion(),
                    sourceValue, source.type(), compiled.plan().targetType(), sourceKnownNonNull, generated);
            out.line("return " + value + ";");

        } else {
            String target = compiled.plan().targetParameter().getSimpleName().toString();

            NodeKind targetKind = types.nodeKind(compiled.plan().targetType());

            if (types.isArrayNode(targetKind)) {
                if (types.writeElementType(compiled.plan().targetType()) == null) {
                    MappingEmissionSupport.error(context, compiled.plan().method(), generated,
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
                                sourceKnownNonNull,
                                generated);

                updates.emitArrayContentsUpdate(
                        out,
                        names,
                        target,
                        updates.materialize(
                                out,
                                names,
                                compiled.plan()
                                        .targetType(),
                                value),
                        targetKind,
                        MappingEmissionSupport.option(context,
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
                        MappingEmissionSupport.option(context,
                                compiled.plan(),
                                "objects",
                                "PUT"),
                        sourceKnownNonNull,
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

        }

        for (MappingCompiler.Assignment assignment :
                compiled.assignments()) {

            boolean presentOnly =
                    sourceReads.emitAutomaticPresenceGuard(
                            out,
                            compiled.plan(),
                            assignment);

            if (updates.emitDirectMapUpdate(
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

            // Direct, single-use property reads can be passed straight to
            // their destination without generating a redundant sourceValue local.
            // Keep locals when a nullable value is tested and written (IGNORE).
            String value = sourceReads.inlineDirectPropertyRead(plan, assignment);
            if (value == null) {
                value = valueEmitter.emitValue(out, names, plan, assignment.value(), generated);
            }

            updates.emitAssignment(
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

        MappingDynamicEmitter.emitDynamicSources(
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

    private void emitNullRootGuard(
            JavaWriter out,
            MappingPlan plan) {

        StringBuilder condition =
                new StringBuilder();

        for (VariableElement source :
                plan.sources()) {

            if (plan.sourceType(source)
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

        if (condition.length() == 0) return;

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

                String cast = context.typeUtils.isAssignable(
                        factory.getReturnType(), creation.targetType())
                        ? "" : "(" + targetType + ") ";
                out.line(targetType + " " + target + " = " + cast + call + ";");

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

                    int groupedEnd = sourceReads.adjacentDirectReadEnd(compiled.plan(), arguments, i);
                    if (groupedEnd > i + 1) {
                        sourceReads.emitAdjacentDirectReads(out, names, compiled.plan(),
                                arguments, i, groupedEnd, values);
                        i = groupedEnd - 1;
                        continue;
                    }

                    MappingCompiler.ConstructorArgument argument =
                            arguments.get(i);

                    values[i] =
                            valueEmitter.emitValue(
                                    out,
                                    names,
                                    compiled.plan(),
                                    argument.value(),
                                    generated,
                                    true);

                    if (argument.value().type().getKind().isPrimitive() &&
                            !sourceReads.isDirectPrimitiveRead(compiled.plan(), argument.value(), true)) {
                        // Nullable indexed reads, scalar conversions and custom
                        // converters may produce a boxed result. Evaluate it
                        // exactly once: an expression-valued converter must not
                        // be invoked twice by the null/default branch.
                        String raw = names.newName("constructorValue");
                        out.line(JavaExpressions.boxedType(types, argument.value().type()) + " " + raw +
                                " = " + values[i] + ";");
                        values[i] = "(" + raw + " == null ? " +
                                MappingEmissionSupport.primitiveDefault(argument.value().type().getKind()) +
                                " : " + raw + ")";
                    }
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

    /**
     * Emits conversion of one already-read source value.
     *
     * <p>The implementation may emit local variables, loops or structural
     * mapping code and returns the expression/local name containing the final
     * converted value.</p>
     */
    public interface ConversionWriter {

        String emit(JavaWriter out, NameAllocator names, MappingPlan plan,
                    ConverterResolver.Conversion conversion, String source,
                    TypeMirror sourceType, TypeMirror targetType, boolean sourceKnownNonNull,
                    GeneratedClass generated);

        void emitMapUpdate(JavaWriter out, NameAllocator names, MappingPlan plan,
                           ConverterResolver.Conversion conversion, String source, TypeMirror sourceType,
                           TypeMirror targetType, String target, String policy, boolean sourceKnownNonNull,
                           GeneratedClass generated);
    }
}
