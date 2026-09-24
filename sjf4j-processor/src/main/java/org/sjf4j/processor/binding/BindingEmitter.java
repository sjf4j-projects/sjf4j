package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

/**
 * Coordinates source emission for compiled binder methods and helpers.
 */
final class BindingEmitter {

    private final ReadEmitter reads;
    private final WriteEmitter writes;

    BindingEmitter(ProcessorContext context) {
        this.reads = new ReadEmitter(context);
        this.writes = new WriteEmitter();
    }

    void emitMethod(
            BindingCompiler.CompiledMethod method,
            GeneratedClass generated) {

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
