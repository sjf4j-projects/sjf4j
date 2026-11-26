package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.CompilationContext;
import org.sjf4j.processor.code.GeneratedClass;

/** Coordinates source emission for compiled binder methods and helpers. */
final class BindingEmitter {

    private final ReadEmitter reads;
    private final WriteEmitter writes;
    private final BindingHelperEmitter helpers;
    private boolean stringHelperEmitted;
    private final boolean fastjson2;
    private final BindingNames names;
    private boolean byteOrderFieldEmitted;

    BindingEmitter(CompilationContext context, BackendSpec backend, BindingNames names,
                   boolean includeNulls, boolean exactNumbers) {
        this.names = names;
        this.fastjson2 = backend.backend() == Backend.FASTJSON2;
        this.reads = new ReadEmitter(context, backend, names, exactNumbers);
        this.writes = new WriteEmitter(backend, names, includeNulls);
        this.helpers = new BindingHelperEmitter(context, reads, writes, names);
    }

    void emitMethod(BindingCompiler.CompiledMethod method, GeneratedClass generated) {
        if (fastjson2 && !byteOrderFieldEmitted
                && method.plan().direction() == BindingPlan.Direction.READ_FROM) {
            String endianField = names.sharedField("FASTJSON2_LITTLE_ENDIAN");
            generated.addField(out -> out.line("private static final boolean " + endianField + " = "
                    + "java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN;"));
            byteOrderFieldEmitted = true;
        }

        if (method.plan().direction() == BindingPlan.Direction.READ_FROM) {
            reads.registerEnums(method.value());
        }
        if (method.plan().direction() == BindingPlan.Direction.WRITE_TO) {
            writes.registerEnums(method.value());
            writes.registerWriteOutput(method.plan().writeOutput());
            if (!stringHelperEmitted && writes.needsStringHelper(method.value())) {
                writes.prepareStringHelpers();
                writes.registerStringHelpers(generated);
                stringHelperEmitted = true;
            }
        }

        generated.addMethod(out -> {
            if (method.plan().direction() == BindingPlan.Direction.READ_FROM) {
                reads.emitMethod(out, method);
            } else {
                writes.emitMethod(out, method);
            }
        });
    }

    void emitHelper(BindingValue value, GeneratedClass generated) {
        reads.registerEnums(value);
        writes.registerEnums(value);
        helpers.emit(value, generated);
    }

    void emitEnumFields(GeneratedClass generated) {
        reads.emitEnumFields(generated);
        if (writes.hasEnumFields()) generated.addField(out -> writes.emitEnumFields(out));
    }

}
