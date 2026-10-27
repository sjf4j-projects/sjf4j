package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Emits direct concrete-generator binding code. */
final class WriteEmitter {

    private final BackendSpec backend;
    private final Fastjson2WriteEmitter fastjson2;
    private final JacksonWriteEmitter jackson;
    private final OtherNativeWriteEmitter other;
    private final StreamingWriteEmitter streaming;

    private final Map<String, String> enumFields = new LinkedHashMap<String, String>();

    WriteEmitter(BackendSpec backend) {
        this.backend = backend;
        this.streaming = new StreamingWriteEmitter(backend);
        switch (backend.backend()) {
            case FASTJSON2:
                fastjson2 = new Fastjson2WriteEmitter(this);
                jackson = null;
                other = null;
                break;
            case JACKSON2:
            case JACKSON3:
                fastjson2 = null;
                jackson = new JacksonWriteEmitter(backend, this);
                other = null;
                break;
            case GSON:
            case JSONP:
                fastjson2 = null;
                jackson = null;
                other = new OtherNativeWriteEmitter(backend);
                break;
            default:
                fastjson2 = null;
                jackson = null;
                other = null;
        }
    }

    void registerEnums(BindingValue root) {
        if (jackson == null && fastjson2 == null) return;
        Set<BindingValue> seen = Collections.newSetFromMap(
                new IdentityHashMap<BindingValue, Boolean>());
        registerEnums(root, seen);
    }

    private void registerEnums(BindingValue value, Set<BindingValue> seen) {
        if (value == null || !seen.add(value)
                || value.direction() != BindingPlan.Direction.WRITE_TO) return;
        if (value.kind() == BindingValue.Kind.ENUM) {
            String type = value.type().toString();
            if (!enumFields.containsKey(type)) enumFields.put(type, "_enum_" + enumFields.size());
        }
        for (BindingProperty property : value.properties()) registerEnums(property.value(), seen);
        registerEnums(value.elementValue(), seen);
        registerEnums(value.mapValue(), seen);
        for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) registerEnums(subtype.compiled, seen);
    }

    String enumField(BindingValue value) {
        String result = enumFields.get(value.type().toString());
        if (result == null) throw new IllegalStateException("Unregistered Enum: " + value.type());
        return result;
    }

    boolean hasEnumFields() { return !enumFields.isEmpty(); }

    void emitEnumFields(JavaWriter out) {
        if (fastjson2 != null) {
            fastjson2.emitEnumFields(out, enumFields);
            return;
        }
        String encoded = backend.backend() == Backend.JACKSON2
                ? "com.fasterxml.jackson.core.io.SerializedString"
                : "tools.jackson.core.io.SerializedString";
        for (Map.Entry<String, String> entry : enumFields.entrySet()) {
            String type = entry.getKey();
            String field = entry.getValue();
            out.line("private static final " + encoded + "[] " + field
                    + " = " + field + "Init();");
            out.beginBlock("private static " + encoded + "[] " + field + "Init()");
            out.line(type + "[] constants = " + type + ".values();");
            out.line(encoded + "[] result = new " + encoded + "[constants.length];");
            out.beginBlock("for (" + type + " constant : constants)");
            out.line("result[constant.ordinal()] = new " + encoded + "(constant.name());");
            out.endBlock();
            out.line("return result;");
            out.endBlock();
        }
    }

    void registerWriteOutput(BindingPlan.WriteOutput output) {
        if (fastjson2 != null) fastjson2.registerWriteOutput(output);
    }

    boolean usesCompiledNames() {
        return backend.usesCompiledNames();
    }

    void emitCompiledNames(JavaWriter out, BindingValue value) {
        int index = 0;
        for (BindingProperty property : value.properties()) {
            String field = compiledNameField(value, index++);
            out.line("private static final " + backend.compiledNameType() + " " +
                    field + " = new " + backend.compiledNameType() + "(" +
                    JavaWriter.stringLiteral(property.name()) + ");");
        }
    }

    static String compiledNameField(BindingValue value, int index) {
        return value.helperName() + "_name_" + index;
    }

    void emitMethod(JavaWriter out, BindingCompiler.CompiledMethod compiled) {
        BindingPlan plan = compiled.plan();
        BindingSource.beginOverride(out, plan.method());
        String source = BindingSource.parameterName(plan, 0);

        // Only the streaming-only backends retain whole-root fallback rules.
        // JSON native backends compile structural nodes and fall back locally.
        if (!usesSharedStructure()) {
            // Preserve streaming-only null-policy and dynamic-root semantics.
            out.beginBlock("if (!this.context.includeNulls)");
            switch (plan.writeOutput()) {
                case STRING:
                    out.line("return this.binder.writeNodeAsString(" + source + ");");
                    break;
                case BYTES:
                    out.line("return this.binder.writeNodeAsBytes(" + source + ");");
                    break;
                case WRITER:
                case OUTPUT_STREAM:
                    out.line("this.binder.writeNode("
                            + BindingSource.parameterName(plan, 1) + ", " + source + ");");
                    out.line("return;");
                    break;
                default:
                    throw new AssertionError(plan.writeOutput());
            }
            out.endBlock();

            if (streaming.shouldFallbackRoot(compiled.value())) {
                switch (plan.writeOutput()) {
                    case STRING:
                        out.line("return this.binder.writeNodeAsString(" + source + ");");
                        break;
                    case BYTES:
                        out.line("return this.binder.writeNodeAsBytes(" + source + ");");
                        break;
                    case WRITER:
                    case OUTPUT_STREAM:
                        out.line("this.binder.writeNode("
                                + BindingSource.parameterName(plan, 1)
                                + ", " + source + ");");
                        break;
                    default:
                        throw new AssertionError(plan.writeOutput());
                }
                BindingSource.endOverride(out);
                return;
            }
        }

        NameAllocator names = methodNames(plan);

        if (backend.backend() == Backend.FASTJSON2) {
            String target = plan.writeOutput() == BindingPlan.WriteOutput.WRITER
                    || plan.writeOutput() == BindingPlan.WriteOutput.OUTPUT_STREAM
                    ? BindingSource.parameterName(plan, 1) : null;
            fastjson2.emitMethod(out, compiled.value(), source, target, plan.writeOutput(), names);
            BindingSource.endOverride(out);
            return;
        }

        switch (plan.writeOutput()) {
            case STRING:
                emitStringMethod(out, compiled.value(), source, names);
                break;

            case BYTES:
                emitBytesMethod(out, compiled.value(), source, names);
                break;

            case WRITER:
            case OUTPUT_STREAM:
                emitStreamMethod(out, compiled.value(), source,
                        BindingSource.parameterName(plan, 1), names);
                break;

            default:
                throw new AssertionError(plan.writeOutput());
        }

        BindingSource.endOverride(out);
    }

    private void emitStringMethod(JavaWriter out, BindingValue value, String source,
                                  NameAllocator names) {
        String output = names.newName("output");
        String generator = names.newName("generator");

        out.line("org.sjf4j.binding.PooledStringWriter " + output
                + " = new org.sjf4j.binding.PooledStringWriter();");
        out.beginBlock("try");
        out.beginBlock("try (" + backend.generatorType() + " " + generator
                + " = this.binder.createGenerator(" + output + "))");
        emitWriteBody(out, value, source, generator, names);
        out.endBlock();
        out.line("return " + output + ".toString();");
        out.endBlock();
        out.beginBlock("finally");
        out.line(output + ".recycle();");
        out.endBlock();
    }

    private void emitBytesMethod(JavaWriter out, BindingValue value, String source,
                                 NameAllocator names) {
        String output = names.newName("output");
        String generator = names.newName("generator");

        // Streaming character backends are faster when they produce UTF-8
        // from the finished String instead of using OutputStreamWriter.
        boolean chars = backend.backend() == Backend.GSON
                || backend.backend() == Backend.SIMPLE
                || backend.backend() == Backend.JSONP;

        String sinkType = chars ? "org.sjf4j.binding.PooledStringWriter"
                : "org.sjf4j.binding.PooledByteArrayOutputStream";
        out.line(sinkType + " " + output + " = new " + sinkType + "();");
        out.beginBlock("try");
        out.beginBlock("try (" + backend.generatorType() + " " + generator
                + " = this.binder.createGenerator(" + output + "))");
        emitWriteBody(out, value, source, generator, names);
        out.endBlock();
        if (chars) {
            out.line("return " + output
                    + ".toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);");
        } else {
            out.line("return " + output + ".toByteArray();");
        }
        out.endBlock();
        out.beginBlock("finally");
        out.line(output + ".recycle();");
        out.endBlock();
    }

    private void emitStreamMethod(JavaWriter out, BindingValue value, String source,
                                  String output, NameAllocator names) {
        String generator = names.newName("generator");

        out.beginBlock("try (" + backend.generatorType() + " " + generator
                + " = this.binder.createGenerator(" + output + "))");
        emitWriteBody(out, value, source, generator, names);
        out.endBlock();
    }

    private void emitWriteBody(JavaWriter out, BindingValue value, String source,
                               String generator, NameAllocator names) {
        out.line(generator + ".start();");

        if (isJackson()) {
            jackson.emitRoot(out, value, source, generator, names);
        } else if (isGsonOrJsonp()) {
            other.emitRoot(out, value, source, generator, names);
        } else {
            streaming.writeValue(out, value, generator, source);
        }

        out.line(generator + ".end();");
    }

    void emitHelper(JavaWriter out, BindingValue value) {
        if (isJackson()) {
            jackson.emitHelper(out, value);
            return;
        }
        if (backend.backend() == Backend.FASTJSON2) {
            fastjson2.emitHelper(out, value);
            return;
        }
        if (isGsonOrJsonp()) {
            other.emitHelper(out, value);
            return;
        }
        streaming.emitHelper(out, value);
    }

    private boolean isJackson() {
        return backend.backend() == Backend.JACKSON2
                || backend.backend() == Backend.JACKSON3;
    }

    private boolean isGsonOrJsonp() {
        return backend.backend() == Backend.GSON
                || backend.backend() == Backend.JSONP;
    }

    private boolean usesSharedStructure() {
        return isJackson() || isGsonOrJsonp()
                || backend.backend() == Backend.FASTJSON2;
    }

    boolean needsStringHelper(BindingValue value) {
        return usesSharedStructure()
                && value.direction() == BindingPlan.Direction.WRITE_TO
                && containsString(value, new HashSet<BindingValue>());
    }

    private boolean containsString(BindingValue value, Set<BindingValue> visited) {
        if (!visited.add(value)) return false;
        switch (value.kind()) {
            case STRING:
                return true;
            case VALUE_CODEC:
                // Jackson raw String codecs also emit _writeString, even when
                // no ordinary String property occurs in the compiled graph.
                return isJackson() && value.codecRawType() != null
                        && "java.lang.String".equals(value.codecRawType().toString());
            case ONE_OF:
                for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
                    if (subtype.compiled != null && containsString(subtype.compiled, visited)) return true;
                }
                return false;
            case POJO:
            case JOJO:
                for (BindingProperty property : value.properties()) {
                    if (containsString(property.value(), visited)) return true;
                }
                return false;
            case LIST:
            case SET:
                return containsString(value.elementValue(), visited);
            case MAP:
                return containsString(value.mapValue(), visited);
            default:
                return false;
        }
    }

    void emitStringHelper(JavaWriter out) {
        String generator;
        String writeString;
        String writeNull;
        switch (backend.backend()) {
            case JACKSON2:
                generator = "com.fasterxml.jackson.core.JsonGenerator";
                writeString = "writeString";
                writeNull = "writeNull";
                break;
            case JACKSON3:
                generator = "tools.jackson.core.JsonGenerator";
                writeString = "writeString";
                writeNull = "writeNull";
                break;
            case FASTJSON2:
                generator = "com.alibaba.fastjson2.JSONWriter";
                writeString = "writeString";
                writeNull = "writeNull";
                break;
            case GSON:
                generator = "com.google.gson.stream.JsonWriter";
                writeString = "value";
                writeNull = "nullValue";
                break;
            case JSONP:
                generator = "jakarta.json.stream.JsonGenerator";
                writeString = "write";
                writeNull = "writeNull";
                break;
            default:
                throw new AssertionError(backend.backend());
        }
        // Native JSON backends pass their existing generator directly.
        out.beginBlock("private static void _writeString(" + generator
                + " g, String value) throws java.io.IOException");
        out.beginBlock("if (value == null)");
        out.line("g." + writeNull + "();");
        out.endBlock(" else {");
        out.indent();
        out.line("g." + writeString + "(value);");
        out.endBlock();
        out.endBlock();

        if (isJackson()) {
            String nameType = backend.backend() == Backend.JACKSON2
                    ? "com.fasterxml.jackson.core.SerializableString"
                    : "tools.jackson.core.io.SerializedString";
            String nameMethod = backend.backend() == Backend.JACKSON2
                    ? "writeFieldName" : "writeName";
            out.beginBlock("private void _writeStringMember(" + generator
                    + " g, " + nameType
                    + " name, String value) throws java.io.IOException");
            out.beginBlock("if (value == null && !this.context.includeNulls)");
            out.line("return;");
            out.endBlock();
            out.line("g." + nameMethod + "(name);");
            out.line("_writeString(g, value);");
            out.endBlock();
        }
    }

    static String propertyExpression(BindingProperty property, String owner) {
        return property.constantString() == null
                ? access(property.access(), owner)
                : JavaWriter.stringLiteral(property.constantString());
    }

    /** Delegates streaming-only helper eligibility to the streaming emitter. */
    boolean requiresWriteHelper(BindingValue value) {
        return usesSharedStructure() || streaming.requiresWriteHelper(value);
    }

    static String access(PropertyAccess access, String owner) {
        if (access.isMethod()) {
            return owner + '.' + access.memberName() + "()";
        }

        return owner + '.' + access.memberName();
    }

    private NameAllocator methodNames(BindingPlan plan) {
        NameAllocator names = new NameAllocator();
        for (javax.lang.model.element.VariableElement parameter :
                plan.method().declaration().getParameters()) {
            names.reserve(parameter.getSimpleName().toString());
        }

        return names;
    }
}
