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
    private final BindingNames names;

    BindingHelperEmitter(ProcessorContext context, ReadEmitter reads, WriteEmitter writes, BindingNames names) {
        this.context = context;
        this.reads = reads;
        this.writes = writes;
        this.names = names;
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
        String source = value.oneOfCreatorIndex() >= 0
                ? "org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                        + value.oneOfOwner() + ".class).creatorInfo.argOneOfInfos["
                        + value.oneOfCreatorIndex() + "]"
                : value.oneOfOwner() == null
                ? "org.sjf4j.meta.TypeRegistry.registerTypeInfo("
                        + context.typeUtils.erasure(value.type()) + ".class).oneOfInfo"
                : "org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                        + value.oneOfOwner() + ".class).propertyLookup.get("
                        + JavaWriter.stringLiteral(value.oneOfProperty()) + ").oneOfInfo";
        String info = names.field(value, "oneOfInfo",
                BindingNames.fieldType(value.type()) + "_ONE_OF_INFO");
        generated.addField(out -> out.line(
                "private static final org.sjf4j.meta.OneOfInfo " + info
                        + " = " + source + ";"));
        if (value.oneOfKey() == null) return;

        // Jackson2's compiled discriminator fast path uses SerializedString.
        String discriminator = names.field(value, "discriminator",
                BindingNames.fieldType(value.type()) + "_DISCRIMINATOR_MATCHER");
        generated.addField(out -> out.line(
                "private static final org.sjf4j.backend.jackson2.binding.Jackson2NameMatcher "
                        + discriminator + " = new "
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
            String name = names.field(value, "subtypePojo:" + i,
                    BindingNames.fieldType(subtype.type) + "_POJO_INFO");
            generated.addField(out -> out.line(
                    "private static final org.sjf4j.meta.PojoInfo " + name
                            + " = org.sjf4j.meta.TypeRegistry.requireRegisteredPojoInfo("
                            + subtype.type + ".class);"));
        }
    }

    private void emitCodecMetadata(BindingValue value, GeneratedClass generated) {
        // Unconstrained reads select a Codec according to the incoming JSON type.
        // Explicit valueFormat and valuePattern remain strict.
        boolean autoRead = value.direction() == BindingPlan.Direction.READ_FROM
                && value.valueFormat() == null && value.valuePattern() == null;
        if (autoRead) {
            String set = names.field(value, "valueSet",
                    BindingNames.lowerType(value.type()) + "ValueSet");
            generated.addField(out -> out.line("private final org.sjf4j.value.ValueSet " + set + ";"));
            generated.addContextInitializer(out -> out.line(
                    "this." + set + " = org.sjf4j.meta.TypeRegistry.registerTypeInfo("
                            + value.type() + ".class).valueSet;"));
        }
        String info = names.field(value, "valueInfo",
                BindingNames.lowerType(value.type()) + "ValueInfo");
        generated.addField(out -> out.line(
                "private final org.sjf4j.value.ValueInfo " + info + ";"));
        if (autoRead) {
            generated.addContextInitializer(out -> out.line(
                    "this." + info + " = this."
                            + names.field(value, "valueSet", BindingNames.lowerType(value.type()) + "ValueSet")
                            + ".selectForRead(this."
                            + names.field(value, "valueSet", BindingNames.lowerType(value.type()) + "ValueSet")
                            + ".defaultInfo.rawJsonType, "
                            + "context.valueFormat(" + value.type() + ".class));"));
        } else {
            generated.addContextInitializer(out -> out.line(
                    "this." + info + " = "
                            + "org.sjf4j.meta.TypeRegistry.requireRegisteredValueInfo("
                            + value.type() + ".class, "
                            + (value.valueFormat() == null
                                    ? "context.valueFormat(" + value.type() + ".class)"
                                    : JavaWriter.stringLiteral(value.valueFormat()))
                            + ", "
                            + (value.valuePattern() == null ? "null"
                                    : JavaWriter.stringLiteral(value.valuePattern()))
                            + ");"));
        }
    }

    private void emitReadHelper(BindingValue value, GeneratedClass generated) {
        if ((value.kind() == BindingValue.Kind.POJO
                || value.kind() == BindingValue.Kind.JOJO) && reads.usesNameMatcher()) {
            reads.matcherFieldName(value);
            generated.addField(out -> reads.emitNameMatcherField(out, value));
        }
        // Register the Fastjson2 fallback helper before source emission.
        reads.prepareHelperNames(value);
        generated.addHelper(out -> reads.emitHelper(out, value));
    }

    private void emitWriteHelper(BindingValue value, GeneratedClass generated) {
        // The streaming-only backend may delegate a whole dynamic graph to Runtime.
        if ((value.kind() == BindingValue.Kind.POJO
                || value.kind() == BindingValue.Kind.JOJO) && writes.usesCompiledNames()) {
            writes.prepareCompiledNames(value);
            generated.addField(out -> writes.emitCompiledNames(out, value));
        }
        generated.addHelper(out -> writes.emitHelper(out, value));
    }
}
