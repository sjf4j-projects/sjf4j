package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

/** Coordinates source emission for compiled binder methods and helpers. */
final class BindingEmitter {

    private final ReadEmitter reads;
    private final WriteEmitter writes;
    private boolean stringHelperEmitted;

    BindingEmitter(
            ProcessorContext context,
            BackendSpec backend) {

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
                value.kind() == BindingValue.Kind.POJO &&
                reads.usesNameMatcher()) {
            generated.addField(
                    out -> reads.emitNameMatcherField(out, value));
        }

        if (value.direction() ==
                BindingPlan.Direction.WRITE_TO &&
                value.kind() == BindingValue.Kind.POJO &&
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
