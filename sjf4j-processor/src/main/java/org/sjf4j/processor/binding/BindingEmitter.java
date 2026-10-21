package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;

/** Coordinates source emission for compiled binder methods and helpers. */
final class BindingEmitter {

    private final ProcessorContext context;

    private final ReadEmitter reads;
    private final WriteEmitter writes;
    private boolean stringHelperEmitted;
    private final boolean fastjson2;
    private boolean byteOrderFieldEmitted;

    BindingEmitter(
            ProcessorContext context,
            BackendSpec backend) {

        this.context = context;
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

        if (value.kind() == BindingValue.Kind.ONE_OF
                && value.direction() == BindingPlan.Direction.READ_FROM) {
            String source = value.oneOfOwner() == null
                    ? "org.sjf4j.meta.TypeRegistry.registerTypeInfo(" + context.typeUtils.erasure(value.type()) + ".class).oneOfInfo"
                    : "org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                            + value.oneOfOwner() + ".class).propertyLookup.get("
                            + JavaWriter.stringLiteral(value.oneOfProperty()) + ").oneOfInfo";
            generated.addField(out -> out.line(
                    "private static final org.sjf4j.meta.OneOfInfo " + value.helperName()
                            + "_info = " + source + ";"));
            if (value.oneOfKey() != null) {
                // The compiled Jackson2 fast path matches the discriminator
                // through JsonParser.nextFieldName(SerializedString).
                generated.addField(out -> out.line(
                        "private static final org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher "
                                + value.helperName() + "_discriminator = new "
                                + "org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher("
                                + JavaWriter.stringLiteral(value.oneOfKey()) + ");"));
                generated.addHelper(out -> reads.emitOneOfReadHelper(out, value));
                for (int i = 0; i < value.oneOfSubtypes().size(); i++) {
                    BindingValue compiled = value.oneOfSubtypes().get(i).compiled;
                    int index = i;
                    generated.addHelper(out -> reads.emitOneOfSubtypeContent(out, value, compiled, index));
                }
            }
            return;
        }
        if (value.kind() == BindingValue.Kind.ONE_OF
                && value.direction() == BindingPlan.Direction.WRITE_TO) {
            for (int i = 0; i < value.oneOfSubtypes().size(); i++) {
                BindingValue.OneOfSubtype subtype = value.oneOfSubtypes().get(i);
                if (!subtype.cachedPojo) continue;
                String name = value.helperName() + "_subtype" + i + "_pojoInfo";
                generated.addField(out -> out.line("private static final org.sjf4j.meta.PojoInfo "
                        + name + " = org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                        + subtype.type + ".class);"));
            }
        }
        if (value.isCodec()) {
            // Only unconstrained reads use JSON-type-driven codec selection.
            // An explicitly named or patterned property remains strict.
            boolean autoRead = value.direction() == BindingPlan.Direction.READ_FROM
                    && value.codecName() == null && value.codecPattern() == null;
            if (autoRead) {
                String set = value.helperName() + "Set";
                generated.addField(out -> out.line(
                        "private final org.sjf4j.value.ValueSet " + set + ";"));
                generated.addContextInitializer(out -> out.line(
                        "this." + set + " = org.sjf4j.meta.TypeRegistry.registerTypeInfo("
                                + value.type() + ".class).valueSet;"));
            }
            generated.addField(out -> out.line(
                    "private final org.sjf4j.value.ValueInfo " + value.helperName() + ";"));
            if (autoRead) {
                // When every codec has the same JSON shape, read directly
                // without peeking a token or invoking generic dispatch.
                generated.addContextInitializer(out -> out.line(
                        "this." + value.helperName() + " = this."
                                + value.helperName() + "Set.selectForRead(this."
                                + value.helperName() + "Set.defaultInfo.rawJsonType, "
                                + "context.valueFormat(" + value.type() + ".class));"));
            } else {
                generated.addContextInitializer(out -> out.line(
                        "this." + value.helperName() + " = "
                                + "org.sjf4j.meta.TypeRegistry.requireRegisteredValueInfo("
                                + value.type() + ".class, "
                                + (value.codecName() == null
                                        ? "context.valueFormat(" + value.type() + ".class)"
                                        : JavaWriter.stringLiteral(value.codecName()))
                                + ", "
                                + (value.codecPattern() == null ? "null"
                                        : JavaWriter.stringLiteral(value.codecPattern()))
                                + ");"));
            }
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
