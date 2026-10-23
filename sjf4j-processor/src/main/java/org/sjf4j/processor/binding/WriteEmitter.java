package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

import java.util.HashSet;
import java.util.Set;

/** Emits direct concrete-writer binding code. */
final class WriteEmitter {


    private final BackendSpec backend;
    private final Boolean fixedIncludeNulls;
    private final Fastjson2WriteEmitter fastjson2;
    private final JacksonWriteEmitter jackson;
    private final OtherNativeWriteEmitter other;
    private final StreamingWriteEmitter streaming;

    WriteEmitter(BackendSpec backend, Boolean fixedIncludeNulls) {
        this.backend = backend;
        this.fixedIncludeNulls = fixedIncludeNulls;
        this.streaming = new StreamingWriteEmitter(backend);
        switch (backend.backend()) {
            case FASTJSON2:
                fastjson2 = new Fastjson2WriteEmitter(fixedIncludeNulls);
                jackson = null;
                other = null;
                break;
            case JACKSON2:
            case JACKSON3:
                fastjson2 = null;
                jackson = new JacksonWriteEmitter(backend, fixedIncludeNulls);
                other = null;
                break;
            case GSON:
            case JSONP:
                fastjson2 = null;
                jackson = null;
                other = new OtherNativeWriteEmitter(backend, fixedIncludeNulls);
                break;
            default:
                fastjson2 = null;
                jackson = null;
                other = null;
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

    void emitMethod(
            JavaWriter out,
            BindingCompiler.CompiledMethod compiled) {

        BindingPlan plan = compiled.plan();

        BindingSource.beginOverride(
                out,
                plan.method());

        String source =
                BindingSource.parameterName(
                        plan,
                        0);

        // Only the streaming-only backends retain whole-root fallback rules.
        // JSON native backends compile structural nodes and fall back locally.
        if (!usesSharedStructure()) {
            // Static includeNulls is a compile-time constant, not a runtime switch.
            boolean dynamicNulls = fixedIncludeNulls == null;
            boolean omitNulls = Boolean.FALSE.equals(fixedIncludeNulls);
            if (dynamicNulls || omitNulls) {
                if (dynamicNulls) out.beginBlock("if (!_FALLBACK_CONTEXT.includeNulls)");
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
                if (dynamicNulls) out.endBlock();
                if (omitNulls) {
                    // The streaming-only backend delegates static null omission
                    // to its Binder; the rest of this generated method is unreachable.
                    BindingSource.endOverride(out);
                    return;
                }
            }

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

        NameAllocator names =
                methodNames(plan);

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
                emitStringMethod(
                        out,
                        compiled.value(),
                        source,
                        names);
                break;

            case BYTES:
                emitBytesMethod(
                        out,
                        compiled.value(),
                        source,
                        names);
                break;

            case WRITER:
            case OUTPUT_STREAM:
                emitStreamMethod(
                        out,
                        compiled.value(),
                        source,
                        BindingSource.parameterName(
                                plan,
                                1),
                        names);
                break;

            default:
                throw new AssertionError(
                        plan.writeOutput());
        }

        BindingSource.endOverride(out);
    }

    private void emitStringMethod(
            JavaWriter out,
            BindingValue value,
            String source,
            NameAllocator names) {

        switch (backend.backend()) {
            case JACKSON2:
            case JACKSON3:
                emitBufferedStringMethod(out, value, source, names);
                return;
            default:
                break;
        }

        String output =
                names.newName("output");

        String writer =
                names.newName("writer");

        out.beginBlock(
                "try (org.sjf4j.binding.FastStringWriter " +
                        output +
                        " = new org.sjf4j.binding.FastStringWriter(); " +
                        backend.writerType() +
                        " " + writer +
                        " = this.binder.createWriter(" +
                        output + "))");

        emitWriteBody(out, value, source, writer, names);

        out.line("return " + output + ".toString();");
        out.endBlock();
    }


    private void emitBufferedStringMethod(
            JavaWriter out,
            BindingValue value,
            String source,
            NameAllocator names) {

        String writer = names.newName("writer");

        out.beginBlock("try (" + backend.writerType() + " " + writer +
                " = this.binder.createWriter())");

        emitWriteBody(out, value, source, writer, names);

        out.line("return " + writer + ".toString();");
        out.endBlock();
    }

    private void emitBytesMethod(
            JavaWriter out,
            BindingValue value,
            String source,
            NameAllocator names) {

        if (isJackson()) {
            String writer = names.newName("writer");
            out.beginBlock("try (" + backend.writerType() + " " + writer
                    + " = this.binder.createWriterUtf8())");
            emitWriteBody(out, value, source, writer, names);
            out.line("return " + writer + ".toBytes();");
            out.endBlock();
            return;
        }

        // Gson, Simple and JSON-P write in-memory byte[] more efficiently through
        // their compiled character path than through OutputStreamWriter.
        // Caller-owned OutputStream methods remain streaming.
        if (backend.backend() == Backend.GSON
                || backend.backend() == Backend.SIMPLE
                || backend.backend() == Backend.JSONP) {
            String output = names.newName("output");
            String writer = names.newName("writer");
            out.beginBlock("try (org.sjf4j.binding.FastStringWriter " + output
                    + " = new org.sjf4j.binding.FastStringWriter(); "
                    + backend.writerType() + " " + writer
                    + " = this.binder.createWriter(" + output + "))");
            emitWriteBody(out, value, source, writer, names);
            out.line("return " + output
                    + ".toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);");
            out.endBlock();
            return;
        }

        String output =
                names.newName("output");

        String writer =
                names.newName("writer");

        out.beginBlock(
                "try (java.io.ByteArrayOutputStream " +
                        output +
                        " = new java.io.ByteArrayOutputStream(); " +
                        backend.writerType() +
                        " " + writer +
                        " = this.binder.createWriter(" +
                        output + "))");

        emitWriteBody(out, value, source, writer, names);

        out.line("return " + output + ".toByteArray();");
        out.endBlock();
    }

    private void emitStreamMethod(
            JavaWriter out,
            BindingValue value,
            String source,
            String output,
            NameAllocator names) {

        String writer =
                names.newName("writer");

        out.line(
                backend.writerType() +
                        " " + writer +
                        " = this.binder.createWriter(" +
                        output + ");");

        emitWriteBody(out, value, source, writer, names);

    }

    private void emitWriteBody(
            JavaWriter out,
            BindingValue value,
            String source,
            String writer,
            NameAllocator names) {

        out.line(writer + ".startDocument();");

        if (isJackson()) {
            jackson.emitRoot(out, value, source, writer, names);
        } else if (isGsonOrJsonp()) {
            other.emitRoot(out, value, source, writer, names);
        } else {
            streaming.writeValue(out, value, writer, source);
        }

        out.line(writer + ".endDocument();");
        out.line(writer + ".flush();");
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
            out.beginBlock("private static void _writeStringMember(" + generator
                    + " g, " + nameType
                    + " name, String value) throws java.io.IOException");
            if (fixedIncludeNulls == null) {
                out.beginBlock("if (value == null && !_FALLBACK_CONTEXT.includeNulls)");
            } else if (!fixedIncludeNulls) {
                out.beginBlock("if (value == null)");
            }
            if (fixedIncludeNulls == null || !fixedIncludeNulls) {
                out.line("return;");
                out.endBlock();
            }
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

    static String access(
            PropertyAccess access,
            String owner) {

        if (access.isMethod()) {
            return owner + '.' +
                    access.memberName() +
                    "()";
        }

        return owner + '.' +
                access.memberName();
    }

    private NameAllocator methodNames(
            BindingPlan plan) {

        NameAllocator names =
                new NameAllocator();

        for (javax.lang.model.element.VariableElement parameter :
                plan.method()
                        .declaration()
                        .getParameters()) {

            names.reserve(
                    parameter.getSimpleName()
                            .toString());
        }

        return names;
    }
}
