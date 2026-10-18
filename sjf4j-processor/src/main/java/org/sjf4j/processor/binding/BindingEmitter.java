package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;

/** Coordinates source emission for compiled binder methods and helpers. */
final class BindingEmitter {

    private final ReadEmitter reads;
    private final WriteEmitter writes;
    private boolean stringHelperEmitted;
    private final boolean fastjson2;
    private boolean byteOrderFieldEmitted;

    BindingEmitter(
            ProcessorContext context,
            BackendSpec backend) {

        this.fastjson2 = backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2;
        this.reads =
                new ReadEmitter(
                        context,
                        backend);

        this.writes =
                new WriteEmitter(
                        backend);
    }

    void emitMethod(
            BindingCompiler.CompiledMethod method,
            GeneratedClass generated) {
        if (fastjson2 && !byteOrderFieldEmitted && method.plan().direction() == BindingPlan.Direction.READ_FROM) {
            generated.addField(out -> out.line("private static final boolean FASTJSON2_LITTLE_ENDIAN = "
                    + "java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;"));
            byteOrderFieldEmitted = true;
        }

        if (method.plan().direction() == BindingPlan.Direction.WRITE_TO) {
            writes.registerWriteOutput(method.plan().writeOutput());
            if (!stringHelperEmitted && writes.needsStringHelper(method.value())) {
                generated.addHelper(out -> writes.emitStringHelper(out));
                stringHelperEmitted = true;
            }
        }

        generated.addMethod(
                out -> {
                    if (method.plan().direction() ==
                            BindingPlan.Direction.READ_FROM) {
                        reads.emitMethod(out, method);
                    } else {
                        writes.emitMethod(out, method);
                    }
                });
    }

    void emitHelper(
            BindingValue value,
            GeneratedClass generated) {

        if (value.isCodec()) {
            // Resolve once per configured instance, never inside the read/write loops.
            // Explicit property formats override RuntimeContext defaults.
            generated.addField(out -> out.line(
                    "private final org.sjf4j.value.ValueInfo " + value.helperName() + ";"));
            generated.addContextInitializer(out -> out.line(
                    "this." + value.helperName() + " = "
                            + "org.sjf4j.meta.TypeRegistry.requireRegisteredValueInfo("
                            + value.type() + ".class, "
                            + (value.codecName() == null
                                    ? "context.defaultValueFormat(" + value.type() + ".class)"
                                    : JavaWriter.stringLiteral(value.codecName()))
                            + ", "
                            + (value.codecPattern() == null ? "null"
                                    : JavaWriter.stringLiteral(value.codecPattern()))
                            + ");"));
            return;
        }
        if (!value.helperRequired()) {
            return;
        }
        // Methods with dynamic type graphs are delegated entirely to Runtime.
        // Do not generate unused StreamingWriter helpers for them.
        if (value.direction() == BindingPlan.Direction.WRITE_TO
                && !writes.requiresWriteHelper(value)) {
            return;
        }

        if (value.direction() ==
                BindingPlan.Direction.READ_FROM &&
                (value.kind() == BindingValue.Kind.POJO || value.kind() == BindingValue.Kind.JOJO) &&
                reads.usesNameMatcher()) {
            generated.addField(
                    out -> reads.emitNameMatcherField(out, value));
        }

        if (value.direction() ==
                BindingPlan.Direction.WRITE_TO &&
                (value.kind() == BindingValue.Kind.POJO
                        || value.kind() == BindingValue.Kind.JOJO) &&
                writes.usesCompiledNames()) {
            generated.addField(
                    out -> writes.emitCompiledNames(out, value));
        }

        generated.addHelper(
                out -> {
                    if (value.direction() ==
                            BindingPlan.Direction.READ_FROM) {
                        reads.emitHelper(out, value);
                    } else {
                        writes.emitHelper(out, value);
                    }
                });
    }
}
