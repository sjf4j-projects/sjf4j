package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;

/** Coordinates source emission for compiled binder methods and helpers. */
final class BindingEmitter {

    private final ReadEmitter reads;
    private final WriteEmitter writes;
    private final BindingHelperEmitter helpers;
    private boolean stringHelperEmitted;
    private final boolean fastjson2;
    private boolean byteOrderFieldEmitted;

    BindingEmitter(
            ProcessorContext context,
            BackendSpec backend,
            Boolean fixedIncludeNulls) {

        this.fastjson2 = backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2;
        this.reads =
                new ReadEmitter(
                        context,
                        backend);

        this.writes =
                new WriteEmitter(
                        backend, fixedIncludeNulls);
        this.helpers = new BindingHelperEmitter(context, reads, writes);
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

    void emitHelper(BindingValue value, GeneratedClass generated) {
        helpers.emit(value, generated);
    }

}
