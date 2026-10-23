package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;

/**
 * Registers generated binder helper methods and their compile-time metadata.
 * Keeps per-instance Codec initialization separate from the method emitter.
 */
final class BindingHelperEmitter {

    private final ProcessorContext context;
    private final ReadEmitter reads;
    private final WriteEmitter writes;

    BindingHelperEmitter(ProcessorContext context, ReadEmitter reads, WriteEmitter writes) {
        this.context = context;
        this.reads = reads;
        this.writes = writes;
    }

    void emit(BindingValue value, GeneratedClass generated) {
        if (value.kind() == BindingValue.Kind.ONE_OF) {
            if (value.direction() == BindingPlan.Direction.READ_FROM) {
                emitOneOfRead(value, generated);
                return;
            }
            emitOneOfWriteMetadata(value, generated);
        }
        if (value.isCodec()) {
            emitCodecMetadata(value, generated);
            return;
        }
        if (!value.helperRequired()) return;

        if (value.direction() == BindingPlan.Direction.READ_FROM) {
            emitReadHelper(value, generated);
        } else if (writes.requiresWriteHelper(value)) {
            emitWriteHelper(value, generated);
        }
    }

    private void emitOneOfRead(BindingValue value, GeneratedClass generated) {
        String source = value.oneOfOwner() == null
                ? "org.sjf4j.meta.TypeRegistry.registerTypeInfo("
                        + context.typeUtils.erasure(value.type()) + ".class).oneOfInfo"
                : "org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                        + value.oneOfOwner() + ".class).propertyLookup.get("
                        + JavaWriter.stringLiteral(value.oneOfProperty()) + ").oneOfInfo";
        generated.addField(out -> out.line(
                "private static final org.sjf4j.meta.OneOfInfo " + value.helperName()
                        + "_info = " + source + ";"));
        if (value.oneOfKey() == null) return;

        // Jackson2's compiled discriminator fast path uses SerializedString.
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

    private void emitOneOfWriteMetadata(BindingValue value, GeneratedClass generated) {
        for (int i = 0; i < value.oneOfSubtypes().size(); i++) {
            BindingValue.OneOfSubtype subtype = value.oneOfSubtypes().get(i);
            if (!subtype.cachedPojo) continue;
            String name = value.helperName() + "_subtype" + i + "_pojoInfo";
            generated.addField(out -> out.line(
                    "private static final org.sjf4j.meta.PojoInfo " + name
                            + " = org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                            + subtype.type + ".class);"));
        }
    }

    private void emitCodecMetadata(BindingValue value, GeneratedClass generated) {
        // Annotated value nodes compile directly to their declared methods
        // and require no codec field or runtime registry lookup.
        if (value.staticNodeValue()) return;
        if (value.staticCodec() != null) {
            org.sjf4j.processor.discovery.ValueCodecMetadata codec = value.staticCodec();
            String valueType = codec.valueType.toString();
            String rawType = codec.rawType.toString();
            String name = value.helperName();
            String reference = codec.declaration.getEnclosingElement().toString()
                    + "." + codec.declaration.getSimpleName();
            String codecType = "org.sjf4j.value.ValueCodec<" + valueType + ", " + rawType + ">";
            String selected = value.codecPattern() == null
                    ? reference
                    : "((org.sjf4j.value.PatternedValueCodec<" + valueType + ", " + rawType
                            + ">) " + reference + ").withPattern("
                            + JavaWriter.stringLiteral(value.codecPattern()) + ")";
            generated.addField(out -> out.line("private static final " + codecType
                    + " " + name + " = " + selected + ";"));
            return;
        }
        // Unconstrained reads select a Codec according to the incoming JSON type.
        // Explicit codecName and codecPattern remain strict.
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
    }

    private void emitReadHelper(BindingValue value, GeneratedClass generated) {
        if ((value.kind() == BindingValue.Kind.POJO
                || value.kind() == BindingValue.Kind.JOJO) && reads.usesNameMatcher()) {
            generated.addField(out -> reads.emitNameMatcherField(out, value));
        }
        generated.addHelper(out -> reads.emitHelper(out, value));
    }

    private void emitWriteHelper(BindingValue value, GeneratedClass generated) {
        // The streaming-only backend may delegate a whole dynamic graph to Runtime.
        if ((value.kind() == BindingValue.Kind.POJO
                || value.kind() == BindingValue.Kind.JOJO) && writes.usesCompiledNames()) {
            generated.addField(out -> writes.emitCompiledNames(out, value));
        }
        generated.addHelper(out -> writes.emitHelper(out, value));
    }
}
