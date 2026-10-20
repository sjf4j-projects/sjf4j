package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

import java.util.HashSet;
import java.util.Set;

/** Emits direct concrete-writer binding code. */
final class WriteEmitter {

    private static final String STREAMING_IO =
            "org.sjf4j.binding.StreamingIO";

    private final BackendSpec backend;
    private final Fastjson2WriteEmitter fastjson2;
    private final JacksonWriteEmitter jackson;
    private final OtherNativeWriteEmitter other;

    WriteEmitter(BackendSpec backend) {
        this.backend = backend;
        switch (backend.backend()) {
            case FASTJSON2:
                fastjson2 = new Fastjson2WriteEmitter();
                jackson = null;
                other = null;
                break;
            case JACKSON2:
            case JACKSON3:
                fastjson2 = null;
                jackson = new JacksonWriteEmitter(backend);
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

        // Omission of null object properties is delegated once at the call
        // boundary; the default compiled native hot path remains unchanged.
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
                out.line("this.binder.writeNode(" +
                        BindingSource.parameterName(plan, 1) + ", " + source + ");");
                out.line("return;");
                break;
            default:
                throw new AssertionError(plan.writeOutput());
        }
        out.endBlock();

        // Dynamic children invalidate whole-method specialization. Delegate to
        // the configured runtime binder rather than emitting mixed static/dynamic code.
        if (!nativeEligible(compiled.value(), new HashSet<BindingValue>())
                && !containsAdaptedValue(compiled.value(),
                        new HashSet<BindingValue>())) {
            switch (plan.writeOutput()) {
                case STRING:
                    out.line("return this.binder.writeNodeAsString(" + source + ");");
                    break;
                case BYTES:
                    out.line("return this.binder.writeNodeAsBytes(" + source + ");");
                    break;
                case WRITER:
                case OUTPUT_STREAM:
                    out.line("this.binder.writeNode(" +
                            BindingSource.parameterName(plan, 1) + ", " + source + ");");
                    break;
                default:
                    throw new AssertionError(plan.writeOutput());
            }
            BindingSource.endOverride(out);
            return;
        }

        NameAllocator names =
                methodNames(plan);

        if (backend.backend() == Backend.FASTJSON2
                && fastjson2.nativeEligible(compiled.value(), new HashSet<BindingValue>())) {
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

        emitWriteBody(out, value, source, writer);

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

        emitWriteBody(out, value, source, writer);

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
            emitWriteBody(out, value, source, writer);
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
            emitWriteBody(out, value, source, writer);
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

        emitWriteBody(out, value, source, writer);

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

        emitWriteBody(out, value, source, writer);

    }

    private void emitWriteBody(
            JavaWriter out,
            BindingValue value,
            String source,
            String writer) {

        out.line(writer + ".startDocument();");

        if (hasNativeJsonWriter() && value.helperRequired()
                && nativeEligible(value, new HashSet<BindingValue>())) {
            out.line(value.helperName() + "(" + writer
                    + "." + "nativeWriter" + "(), " + source + ");");
        } else {
            writeValue(out, value, writer, source);
        }

        out.line(writer + ".endDocument();");
        out.line(writer + ".flush();");
    }

    void emitHelper(JavaWriter out, BindingValue value) {
        // Fastjson2 accepts ValueCodec in native helpers while the generic
        // streaming writer considers those subtrees non-native. A mixed POJO
        // can therefore call this helper with Fastjson2Writer even when the
        // same helper has native UTF-8/UTF-16 variants.
        boolean fastjsonNative = backend.backend() == Backend.FASTJSON2
                && fastjson2.nativeEligible(value, new HashSet<BindingValue>());
        if (fastjsonNative) {
            fastjson2.emitHelper(out, value);
            if (!containsAdaptedValue(value, new HashSet<BindingValue>())) return;
        }
        // Generate a single native helper when the full type graph is concrete.
        // Unresolved graphs retain the StreamingWriter fallback.
        if (!fastjsonNative && hasNativeJsonWriter() && nativeEligible(value,
                new HashSet<BindingValue>())) {
            switch (backend.backend()) {
                case JACKSON2:
                case JACKSON3:
                    jackson.emitHelper(out, value);
                    return;
                case FASTJSON2:
                    fastjson2.emitHelper(out, value);
                    return;
                case GSON:
                case JSONP:
                    other.emitHelper(out, value);
                    return;
                default:
                    throw new AssertionError(backend.backend());
            }
        }

        out.beginBlock("private void " + value.helperName()
                + "(" + backend.writerType() + " writer, "
                + value.type() + " value) throws java.io.IOException");
        switch (value.kind()) {
            case ONE_OF:
                emitOneOf(out, value);
                break;
            case POJO:
            case JOJO:
                emitPojo(out, value);
                break;
            case JSON_OBJECT:
            case JSON_ARRAY:
                out.line(STREAMING_IO + ".writeNode(writer, value, "
                        + "this.context);");
                break;
            case LIST:
            case SET:
                emitCollection(out, value);
                break;
            case MAP:
                emitMap(out, value);
                break;
            default:
                throw new AssertionError("No write helper for " + value.kind());
        }
        out.endBlock();
    }

    private boolean hasNativeJsonWriter() {
        switch (backend.backend()) {
            case JACKSON2:
            case JACKSON3:
            case FASTJSON2:
            case GSON:
            case JSONP:
                return true;
            default:
                return false;
        }
    }

    private boolean isJackson() {
        return backend.backend() == Backend.JACKSON2
                || backend.backend() == Backend.JACKSON3;
    }

    boolean needsStringHelper(BindingValue value) {
        if (!hasNativeJsonWriter()
                || value.direction() != BindingPlan.Direction.WRITE_TO) {
            return false;
        }
        if (backend.backend() == Backend.FASTJSON2
                && fastjson2.nativeEligible(value, new HashSet<BindingValue>())) {
            return containsString(value, new HashSet<BindingValue>());
        }
        return containsNativeString(value, new HashSet<BindingValue>());
    }

    private boolean containsNativeString(BindingValue value, Set<BindingValue> visited) {
        if (!visited.add(value)) return false;
        if (value.helperRequired()
                && nativeEligible(value, new HashSet<BindingValue>())) {
            return containsString(value, new HashSet<BindingValue>());
        }
        switch (value.kind()) {
            case ONE_OF:
                for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
                    if (subtype.compiled != null
                            && containsNativeString(subtype.compiled, visited)) return true;
                }
                return false;
            case POJO:
            case JOJO:
                for (BindingProperty property : value.properties()) {
                    if (containsNativeString(property.value(), visited)) return true;
                }
                return false;
            case LIST:
            case SET:
                return containsNativeString(value.elementValue(), visited);
            case MAP:
                return containsNativeString(value.mapValue(), visited);
            default:
                return false;
        }
    }

    private boolean containsString(BindingValue value, Set<BindingValue> visited) {
        if (!visited.add(value)) return false;
        switch (value.kind()) {
            case STRING:
                return true;
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
        out.beginBlock("private static void _writeString(" + generator
                + " g, String value) throws java.io.IOException");
        out.beginBlock("if (value == null)");
        out.line("g." + writeNull + "();");
        out.endBlock(" else {");
        out.indent();
        out.line("g." + writeString + "(value);");
        out.endBlock();
        out.endBlock();
    }

    private void emitOneOf(JavaWriter out, BindingValue value) {
        emitNullReturn(out);
        for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
            if (subtype.compiled == null) continue;
            String type = subtype.type.toString();
            out.beginBlock("if (value.getClass() == " + type + ".class)");
            String expression = "(" + type + ") value";
            if (backend.backend() == Backend.FASTJSON2
                    && subtype.compiled.helperRequired()
                    && fastjson2.nativeEligible(subtype.compiled, new HashSet<BindingValue>())) {
                fastjson2.emitMappedSubtype(out, subtype.compiled, "writer", expression);
            } else {
                writeValue(out, subtype.compiled, "writer", expression);
            }
            out.line("return;");
            out.endBlock();
        }
        // Unknown classes and subtypes with inaccessible fields retain Runtime semantics.
        out.line(STREAMING_IO + ".writeNode(writer, value, this.context);");
    }

    private void emitPojo(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("writer.startObject();");

        int index = 0;

        for (BindingProperty property :
                value.properties()) {

            if (index > 0) {
                out.line("writer.separateProperty();");
            }

            out.line("writer.writeName(" +
                    (backend.usesCompiledNames()
                            ? compiledNameField(value, index)
                            : JavaWriter.stringLiteral(property.name())) +
                    ");");
            index++;

            writeValue(
                    out,
                    property.value(),
                    "writer",
                    access(
                            property.access(),
                            "value"));
        }

        if (value.kind() == BindingValue.Kind.JOJO && value.writeDynamic()) {
            out.line("int count = " + index + ";");
            out.beginBlock("for (java.util.Map.Entry<String, Object> entry : "
                    + "value.dynamicProperties().entrySet())");
            out.line("writer.writeName(entry.getKey(), count++ > 0);");
            out.line(STREAMING_IO + ".writeNode(writer, entry.getValue(), "
                    + "this.context);");
            out.endBlock();
        }

        out.line("writer.endObject();");
    }

    private void emitCollection(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("writer.startArray();");
        out.line("int count = 0;");

        out.beginBlock(
                "for (" +
                        value.elementValue().type() +
                        " element : value)");

        out.beginBlock("if (count++ > 0)");
        out.line("writer.separateElement();");
        out.endBlock();

        writeValue(
                out,
                value.elementValue(),
                "writer",
                "element");

        out.endBlock();
        out.line("writer.endArray();");
    }

    private void emitMap(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("writer.startObject();");
        out.line("int count = 0;");

        out.beginBlock(
                "for (java.util.Map.Entry<?, ?> entry : value.entrySet())");

        out.beginBlock("if (count++ > 0)");
        out.line("writer.separateProperty();");
        out.endBlock();

        out.line(
                "writer.writeName((String) entry.getKey());");

        writeValue(
                out,
                value.mapValue(),
                "writer",
                "(" +
                        value.mapValue().type() +
                        ") entry.getValue()");

        out.endBlock();
        out.line("writer.endObject();");
    }

    private void writeValue(
            JavaWriter out,
            BindingValue value,
            String writer,
            String expression) {

        if (value.helperRequired()) {
            // Dynamic containers without adapted descendants have no generated
            // helper. A mixed compiled parent can still reach them.
            if (!requiresWriteHelper(value)) {
                out.line(STREAMING_IO + ".writeNode(" + writer + ", "
                        + expression + ", this.context);");
                return;
            }
            // A mixed POJO graph uses StreamingWriter for its parent, but a
            // fully concrete child may have a native-only helper signature.
            if (hasNativeJsonWriter()
                    && nativeEligible(value, new HashSet<BindingValue>())) {
                if (backend.backend() == Backend.FASTJSON2) {
                    // Fastjson2 native helpers are separately generated for UTF-8
                    // and UTF-16. A streaming parent has no native-mode helper.
                    out.line(STREAMING_IO + ".writeNode(" + writer + ", "
                            + expression + ", this.context);");
                } else {
                    out.line(value.helperName() + "(" + writer + "."
                            + "nativeWriter" + "(), " + expression + ");");
                }
            } else {
                out.line(value.helperName() + "(" + writer + ", " + expression + ");");
            }
            return;
        }

        switch (value.kind()) {
            case VALUE_CODEC:
                out.beginBlock("");
                out.line(value.type() + " codecValue = " + expression + ";");
                out.beginBlock("if (codecValue == null)");
                out.line(writer + ".writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line(writer + ".writeRaw("
                        + value.helperName() + ".valueToRaw(codecValue), this.context);");
                out.endBlock();
                out.endBlock();
                return;
            case STRING:
                out.line(
                        writer +
                                ".writeString(" +
                                expression +
                                ");");
                return;

            case CHARACTER:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Char");
                return;

            case BOOLEAN:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Boolean");
                return;

            case BYTE:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Byte");
                return;

            case SHORT:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Short");
                return;

            case INT:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Int");
                return;

            case LONG:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Long");
                return;

            case FLOAT:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Float");
                return;

            case DOUBLE:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        writer,
                        expression,
                        "Double");
                return;

            case NUMBER:
                out.line(
                        writer +
                                ".writeNumber(" +
                                expression +
                                ");");
                return;

            case BIG_INTEGER:
                out.line(
                        writer +
                                ".writeBigInteger(" +
                                expression +
                                ");");
                return;

            case BIG_DECIMAL:
                out.line(
                        writer +
                                ".writeBigDecimal(" +
                                expression +
                                ");");
                return;

            case ENUM:
                out.beginBlock(
                        "if (" + expression +
                                " == null)");
                out.line(writer + ".writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line(
                        writer +
                                ".writeStringValue(" +
                                expression +
                                ".name());");
                out.endBlock();
                return;

            case JSON_OBJECT:
            case JSON_ARRAY:
                out.line(STREAMING_IO + ".writeNode(" + writer + ", "
                        + expression + ", " + "this.context);");
                return;

            case RUNTIME:
                out.line(
                        STREAMING_IO +
                                ".writeNode(" +
                                writer + ", " +
                                expression + ", " +
                                "this.context);");
                return;

            default:
                throw new AssertionError(
                        "Unsupported write kind " +
                                value.kind());
        }
    }

    private void emitPrimitiveOrBoxed(
            JavaWriter out,
            BindingValue value,
            String writer,
            String expression,
            String suffix) {

        out.line(
                writer +
                        (value.primitive()
                                ? ".write" + suffix + "Value("
                                : ".write" + suffix + "(") +
                        expression +
                        ");");
    }

    private void emitNullReturn(JavaWriter out) {
        out.beginBlock("if (value == null)");
        out.line("writer.writeNull();");
        out.line("return;");
        out.endBlock();
    }


    /** Native helpers require a fully concrete type graph. */
    boolean requiresWriteHelper(BindingValue value) {
        return nativeEligible(value, new HashSet<BindingValue>())
                || containsAdaptedValue(value, new HashSet<BindingValue>());
    }

    /** Codec subtrees use compiled StreamingWriter, preserving native POJO fast paths. */
    private boolean containsAdaptedValue(BindingValue value,
            Set<BindingValue> visited) {
        if (!visited.add(value)) return false;
        switch (value.kind()) {
            case VALUE_CODEC:
            case ONE_OF:
            case JOJO:
            case JSON_OBJECT:
            case JSON_ARRAY:
                return true;
            case POJO:
                for (BindingProperty property : value.properties()) {
                    if (containsAdaptedValue(property.value(), visited)) return true;
                }
                return false;
            case LIST:
            case SET:
                return containsAdaptedValue(value.elementValue(), visited);
            case MAP:
                return containsAdaptedValue(value.mapValue(), visited);
            default:
                return false;
        }
    }

    private boolean nativeEligible(BindingValue value,
            Set<BindingValue> visited) {
        if (!visited.add(value)) return true;
        switch (value.kind()) {
            case RUNTIME:
            case ONE_OF:
            case NUMBER:
            case VALUE_CODEC:
            case JOJO:
            case JSON_OBJECT:
            case JSON_ARRAY:
                return false;
            case POJO:
                for (BindingProperty p : value.properties()) {
                    if (!nativeEligible(p.value(), visited)) return false;
                }
                return true;
            case LIST:
            case SET:
                return nativeEligible(value.elementValue(), visited);
            case MAP:
                return nativeEligible(value.mapValue(), visited);
            default:
                return true;
        }
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
