package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

/** Emits direct concrete-writer binding code. */
final class WriteEmitter {

    private static final String STREAMING_IO =
            "org.sjf4j.binding.StreamingIO";

    private static final String RUNTIME_CONTEXT =
            "org.sjf4j.RuntimeContext";

    private final BackendSpec backend;

    private enum FastjsonMode {
        UTF8("Utf8"), UTF16("Utf16");

        final String suffix;

        FastjsonMode(String suffix) {
            this.suffix = suffix;
        }
    }

    private final java.util.Set<FastjsonMode> fastjsonModes =
            java.util.EnumSet.noneOf(FastjsonMode.class);
    void registerWriteOutput(BindingPlan.WriteOutput output) {
        if (backend.backend() != org.sjf4j.annotation.binding.Backend.FASTJSON2) return;
        switch (output) {
            case STRING:
            case WRITER:
                fastjsonModes.add(FastjsonMode.UTF16);
                break;
            case BYTES:
            case OUTPUT_STREAM:
                fastjsonModes.add(FastjsonMode.UTF8);
                break;
            default:
                throw new AssertionError(output);
        }
    }

    WriteEmitter(BackendSpec backend) {
        this.backend = backend;
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

    private String compiledNameField(BindingValue value, int index) {
        return value.helperName() + "_name_" + index;
    }

    private static boolean fixedRawName(String name) {
        int length = name.length();
        if (length < 2 || length > 16) return false;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c > 0x7E || c == '"' || c == '\\') return false;
        }
        return true;
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

        // Dynamic children invalidate whole-method specialization. Delegate to
        // the configured runtime binder rather than emitting mixed static/dynamic code.
        if (!nativeEligible(compiled.value(), new java.util.HashSet<BindingValue>())) {
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

        if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2) {
            String target = plan.writeOutput() == BindingPlan.WriteOutput.WRITER
                    || plan.writeOutput() == BindingPlan.WriteOutput.OUTPUT_STREAM
                    ? BindingSource.parameterName(plan, 1) : null;
            emitFastjsonMethod(out, compiled.value(), source, target, plan.writeOutput(), names);
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

    /** Emits Fastjson2 code using only its native writer on the compiled path. */
    private void emitFastjsonMethod(
            JavaWriter out,
            BindingValue value,
            String source,
            String target,
            BindingPlan.WriteOutput output,
            NameAllocator names) {

        boolean utf8 = output == BindingPlan.WriteOutput.BYTES
                || output == BindingPlan.WriteOutput.OUTPUT_STREAM;
        FastjsonMode mode = utf8 ? FastjsonMode.UTF8 : FastjsonMode.UTF16;
        String writer = names.newName("nativeWriter");

        out.beginBlock("try (com.alibaba.fastjson2.JSONWriter " + writer
                + " = this.binder." + (utf8 ? "createNativeWriterUtf8()" : "createNativeWriter()")
                + ")");

        // Feature-sensitive escaped field names cannot be written as raw bytes/chars.
        // Return to the configured runtime Binder, never to a second Safe helper.
        out.beginBlock("if (!org.sjf4j.backend.fastjson2.binding.Fastjson2Binder."
                + (utf8 ? "supportsRawUtf8Names(" : "supportsRawUtf16Names(")
                + writer + "))");
        switch (output) {
            case STRING:
                out.line("return this.binder.writeNodeAsString(" + source + ");");
                break;
            case BYTES:
                out.line("return this.binder.writeNodeAsBytes(" + source + ");");
                break;
            case WRITER:
            case OUTPUT_STREAM:
                out.line("this.binder.writeNode(" + target + ", " + source + ");");
                out.line("return;");
                break;
            default:
                throw new AssertionError(output);
        }
        out.endBlock();

        if (value.helperRequired()) {
            out.line(fastjsonHelperName(value, mode) + "("
                    + writer + ", " + source + ");");
        } else {
            emitNativeValue(out, value, source, mode, writer, names.newName("item"));
        }

        switch (output) {
            case STRING:
                out.line("return " + writer + ".toString();");
                break;
            case BYTES:
                out.line("return " + writer + ".getBytes();");
                break;
            case WRITER:
            case OUTPUT_STREAM:
                out.line(writer + ".flushTo(" + target + ");");
                break;
            default:
                throw new AssertionError(output);
        }
        out.endBlock();
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
        if (backend.backend() == org.sjf4j.annotation.binding.Backend.GSON
                || backend.backend() == org.sjf4j.annotation.binding.Backend.SIMPLE
                || backend.backend() == org.sjf4j.annotation.binding.Backend.JSONP) {
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
                && nativeEligible(value, new java.util.HashSet<BindingValue>())) {
            out.line(value.helperName() + "(" + writer
                    + "." + nativeJsonAccessor() + "(), " + source + ");");
        } else {
            writeValue(out, value, writer, source);
        }

        out.line(writer + ".endDocument();");
        out.line(writer + ".flush();");
    }

    void emitHelper(JavaWriter out, BindingValue value) {
        // Generate a single native helper when the full type graph is concrete.
        // Unresolved graphs retain the StreamingWriter fallback.
        if (hasNativeJsonWriter() && nativeEligible(value,
                new java.util.HashSet<BindingValue>())) {
            switch (backend.backend()) {
                case JACKSON2:
                case JACKSON3:
                    emitJacksonNativeHelper(out, value);
                    return;
                case FASTJSON2:
                    for (FastjsonMode mode : fastjsonModes) {
                        emitNativeHelper(out, value, mode);
                    }
                    return;
                case GSON:
                case JSONP:
                    emitOtherNativeHelper(out, value);
                    return;
                default:
                    throw new AssertionError(backend.backend());
            }
        }

        out.beginBlock("private static void " + value.helperName()
                + "(" + backend.writerType() + " writer, "
                + value.type() + " value) throws java.io.IOException");
        switch (value.kind()) {
            case POJO:
                emitPojo(out, value);
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

    private String nativeJsonAccessor() {
        return backend.backend() == org.sjf4j.annotation.binding.Backend.GSON
                || backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2
                ? "nativeWriter" : "nativeGenerator";
    }

    private boolean isJackson() {
        return backend.backend() == org.sjf4j.annotation.binding.Backend.JACKSON2
                || backend.backend() == org.sjf4j.annotation.binding.Backend.JACKSON3;
    }

    private String jacksonGeneratorType() {
        return backend.backend() == org.sjf4j.annotation.binding.Backend.JACKSON2
                ? "com.fasterxml.jackson.core.JsonGenerator"
                : "tools.jackson.core.JsonGenerator";
    }

    private void emitJacksonNativeHelper(JavaWriter out, BindingValue value) {
        boolean jackson2 = backend.backend() == org.sjf4j.annotation.binding.Backend.JACKSON2;
        out.beginBlock("private static void " + value.helperName() + "("
                + jacksonGeneratorType() + " g, " + value.type()
                + " value) throws java.io.IOException");
        out.beginBlock("if (value == null)");
        out.line("g.writeNull();");
        out.line("return;");
        out.endBlock();
        switch (value.kind()) {
            case POJO:
                out.line("g.writeStartObject();");
                int index = 0;
                for (BindingProperty property : value.properties()) {
                    String field = compiledNameField(value, index++) + ".serializedName";
                    BindingValue child = property.value();
                    String expr = access(property.access(), "value");
                    if (child.kind() == BindingValue.Kind.STRING) {
                        out.line("_writeMember(g, " + field + ", " + expr + ");");
                    } else {
                        out.line("g." + (jackson2 ? "writeFieldName" : "writeName")
                                + "(" + field + ");");
                        emitJacksonNativeValue(out, child, expr);
                    }
                }
                out.line("g.writeEndObject();");
                break;
            case LIST:
                out.line("g.writeStartArray();");
                out.beginBlock("for (int i = 0, n = value.size(); i < n; i++)");
                emitJacksonNativeValue(out, value.elementValue(), "value.get(i)");
                out.endBlock();
                out.line("g.writeEndArray();");
                break;
            case SET:
                out.line("g.writeStartArray();");
                out.beginBlock("for (" + value.elementValue().type() + " element : value)");
                emitJacksonNativeValue(out, value.elementValue(), "element");
                out.endBlock();
                out.line("g.writeEndArray();");
                break;
            case MAP:
                out.line("g.writeStartObject();");
                out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
                out.line("g." + (jackson2 ? "writeFieldName" : "writeName")
                        + "((String) entry.getKey());");
                emitJacksonNativeValue(out, value.mapValue(),
                        "(" + value.mapValue().type() + ") entry.getValue()");
                out.endBlock();
                out.line("g.writeEndObject();");
                break;
            default:
                throw new AssertionError(value.kind());
        }
        out.endBlock();
    }

    boolean needsNativeStringMember(BindingValue value) {
        if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2) return false;
        if (!hasNativeJsonWriter() || value.direction() != BindingPlan.Direction.WRITE_TO
                || value.kind() != BindingValue.Kind.POJO
                || !nativeEligible(value, new java.util.HashSet<BindingValue>())) {
            return false;
        }
        for (BindingProperty property : value.properties()) {
            if (property.value().kind() == BindingValue.Kind.STRING) return true;
        }
        return false;
    }

    void emitNativeStringMember(JavaWriter out) {
        boolean jackson2 = backend.backend() == org.sjf4j.annotation.binding.Backend.JACKSON2;
        String nameType;
        String generatorType;
        String fieldMethod;
        String stringMethod;
        String nullMethod;
        switch (backend.backend()) {
            case JACKSON2:
                nameType = "com.fasterxml.jackson.core.io.SerializedString";
                generatorType = "com.fasterxml.jackson.core.JsonGenerator";
                fieldMethod = "writeFieldName"; stringMethod = "writeString"; nullMethod = "writeNull";
                break;
            case JACKSON3:
                nameType = "tools.jackson.core.io.SerializedString";
                generatorType = "tools.jackson.core.JsonGenerator";
                fieldMethod = "writeName"; stringMethod = "writeString"; nullMethod = "writeNull";
                break;
            case FASTJSON2:
                nameType = "org.sjf4j.backend.fastjson2.binding.Fastjson2CompiledName";
                generatorType = "com.alibaba.fastjson2.JSONWriter";
                fieldMethod = "_writeName"; stringMethod = "writeString"; nullMethod = "writeNull";
                break;
            case GSON:
                nameType = "String";
                generatorType = "com.google.gson.stream.JsonWriter";
                fieldMethod = "name"; stringMethod = "value"; nullMethod = "nullValue";
                break;
            case JSONP:
                nameType = "String";
                generatorType = "jakarta.json.stream.JsonGenerator";
                fieldMethod = "writeKey"; stringMethod = "write"; nullMethod = "writeNull";
                break;
            default:
                throw new AssertionError(backend.backend());
        }
        out.beginBlock("private static void _writeMember(" + generatorType
                + " g, " + nameType + " name, String value"
                + (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2
                ? ", int nameMode" : "") + ") throws java.io.IOException");
        if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2) {
            out.line("_writeName(g, name, nameMode);");
        } else {
            out.line("g." + fieldMethod + "(name);");
        }
        out.beginBlock("if (value == null)");
        out.line("g." + nullMethod + "();");
        out.endBlock(" else {");
        out.indent();
        out.line("g." + stringMethod + "(value);");
        out.endBlock();
        out.endBlock();
    }

    private void emitJacksonNativeValue(JavaWriter out, BindingValue child, String expr) {
        if (child.helperRequired()) {
            out.line(child.helperName() + "(g, " + expr + ");");
            return;
        }
        if (child.kind() == BindingValue.Kind.STRING) {
            out.line("g.writeString(" + expr + ");");
            return;
        }
        String method;
        switch (child.kind()) {
            case BOOLEAN: method = "writeBoolean"; break;
            case BYTE: case SHORT: case INT: case LONG:
            case FLOAT: case DOUBLE: case BIG_INTEGER: case BIG_DECIMAL:
                method = "writeNumber"; break;
            case ENUM:
                out.beginBlock("");
                out.line(child.type() + " item = " + expr + ";");
                out.line("g.writeString(item == null ? null : item.name());");
                out.endBlock();
                return;
            case CHARACTER:
                if (child.primitive()) {
                    out.line("g.writeString(Character.toString(" + expr + "));");
                } else {
                    out.beginBlock("");
                    out.line(child.type() + " item = " + expr + ";");
                    out.line("g.writeString(item == null ? null : item.toString());");
                    out.endBlock();
                }
                return;
            default:
                throw new AssertionError("Unsupported Jackson native value " + child.kind());
        }
        if (child.primitive()) {
            out.line("g." + method + "(" + expr + ");");
        } else {
            out.beginBlock("");
            out.line(child.type() + " item = " + expr + ";");
            out.beginBlock("if (item == null)");
            out.line("g.writeNull();");
            out.endBlock(" else {");
            out.indent();
            out.line("g." + method + "(item);");
            out.endBlock();
            out.endBlock();
        }
    }


    private void emitOtherNativeHelper(JavaWriter out, BindingValue value) {
        boolean gson = backend.backend() == org.sjf4j.annotation.binding.Backend.GSON;
        String generatorType = gson
                ? "com.google.gson.stream.JsonWriter"
                : "jakarta.json.stream.JsonGenerator";
        String beginObject = gson ? "beginObject" : "writeStartObject";
        String endObject = gson ? "endObject" : "writeEnd";
        String beginArray = gson ? "beginArray" : "writeStartArray";
        String endArray = gson ? "endArray" : "writeEnd";
        String nullMethod = gson ? "nullValue" : "writeNull";
        String nameMethod = gson ? "name" : "writeKey";
        out.beginBlock("private static void " + value.helperName() + "("
                + generatorType + " g, " + value.type() + " value) throws java.io.IOException");
        out.beginBlock("if (value == null)");
        out.line("g." + nullMethod + "();");
        out.line("return;");
        out.endBlock();
        switch (value.kind()) {
            case POJO:
                out.line("g." + beginObject + "();");
                for (BindingProperty property : value.properties()) {
                    String expr = access(property.access(), "value");
                    if (property.value().kind() == BindingValue.Kind.STRING) {
                        out.line("_writeMember(g, "
                                + JavaWriter.stringLiteral(property.name()) + ", "
                                + expr + ");");
                    } else {
                        out.line("g." + nameMethod + "("
                                + JavaWriter.stringLiteral(property.name()) + ");");
                        emitOtherNativeValue(out, property.value(), expr);
                    }
                }
                out.line("g." + endObject + "();");
                break;
            case LIST:
            case SET:
                out.line("g." + beginArray + "();");
                out.beginBlock("for (" + value.elementValue().type() + " element : value)");
                emitOtherNativeValue(out, value.elementValue(), "element");
                out.endBlock();
                out.line("g." + endArray + "();");
                break;
            case MAP:
                out.line("g." + beginObject + "();");
                out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
                out.line("g." + nameMethod + "((String) entry.getKey());");
                emitOtherNativeValue(out, value.mapValue(),
                        "(" + value.mapValue().type() + ") entry.getValue()");
                out.endBlock();
                out.line("g." + endObject + "();");
                break;
            default:
                throw new AssertionError(value.kind());
        }
        out.endBlock();
    }

    private void emitOtherNativeValue(JavaWriter out, BindingValue child, String expr) {
        boolean gson = backend.backend() == org.sjf4j.annotation.binding.Backend.GSON;
        String scalarMethod = gson ? "value" : "write";
        String nullMethod = gson ? "nullValue" : "writeNull";
        if (child.helperRequired()) {
            out.line(child.helperName() + "(g, " + expr + ");");
            return;
        }
        if (child.kind() == BindingValue.Kind.ENUM || child.kind() == BindingValue.Kind.CHARACTER) {
            if (child.primitive()) {
                out.line("g." + scalarMethod + "(Character.toString(" + expr + "));");
            } else {
                out.beginBlock("");
                out.line(child.type() + " item = " + expr + ";");
                out.beginBlock("if (item == null)");
                out.line("g." + nullMethod + "();");
                out.endBlock(" else {");
                out.indent();
                out.line("g." + scalarMethod + "(item."
                        + (child.kind() == BindingValue.Kind.ENUM ? "name()" : "toString()")
                        + ");");
                out.endBlock();
                out.endBlock();
            }
            return;
        }
        String valueExpr = "item";
        if (child.primitive()) {
            String rawExpr = expr;
            if (!gson && (child.kind() == BindingValue.Kind.BYTE
                    || child.kind() == BindingValue.Kind.SHORT)) {
                rawExpr = "(int) (" + expr + ")";
            } else if (!gson && child.kind() == BindingValue.Kind.FLOAT) {
                rawExpr = "(double) (" + expr + ")";
            }
            out.line("g." + scalarMethod + "(" + rawExpr + ");");
        } else {
            out.beginBlock("");
            out.line(child.type() + " item = " + expr + ";");
            out.beginBlock("if (item == null)");
            out.line("g." + nullMethod + "();");
            out.endBlock(" else {");
            out.indent();
            if (!gson && (child.kind() == BindingValue.Kind.BYTE
                    || child.kind() == BindingValue.Kind.SHORT)) {
                valueExpr = "item.intValue()";
            } else if (child.kind() == BindingValue.Kind.FLOAT) {
                // Both adapters widen boxed float to double before serialization.
                valueExpr = "item.doubleValue()";
            }
            out.line("g." + scalarMethod + "(" + valueExpr + ");");
            out.endBlock();
            out.endBlock();
        }
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
            out.line(
                    value.helperName() +
                            "(" + writer +
                            ", " + expression +
                            ");");
            return;
        }

        switch (value.kind()) {
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

            case RUNTIME:
                out.line(
                        STREAMING_IO +
                                ".writeNode(" +
                                writer + ", " +
                                expression + ", " +
                                RUNTIME_CONTEXT +
                                ".EMPTY);");
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

        if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2
                && value.primitive()) {
            String nativeMethod;
            switch (suffix) {
                case "Boolean": nativeMethod = "writeBool"; break;
                case "Byte": nativeMethod = "writeInt8"; break;
                case "Short": nativeMethod = "writeInt16"; break;
                case "Int": nativeMethod = "writeInt32"; break;
                case "Long": nativeMethod = "writeInt64"; break;
                case "Float": nativeMethod = "writeFloat"; break;
                case "Double": nativeMethod = "writeDouble"; break;
                default: nativeMethod = null; break;
            }
            if (nativeMethod != null) {
                out.line(writer + ".nativeWriter()." + nativeMethod + "(" + expression + ");");
                return;
            }
        }
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


    /** Experimental native UTF-8 helper; dynamic/runtime nodes keep the adapter path. */
    boolean requiresWriteHelper(BindingValue value) {
        return nativeEligible(value, new java.util.HashSet<BindingValue>());
    }

    private boolean nativeEligible(BindingValue value,
            java.util.Set<BindingValue> visited) {
        if (!visited.add(value)) return true;
        switch (value.kind()) {
            case RUNTIME:
            case NUMBER:
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

    private String fastjsonHelperName(BindingValue value, FastjsonMode mode) {
        return value.helperName() + mode.suffix;
    }

    private String fastjsonNameExpr(String name, FastjsonMode mode) {
        switch (mode) {
            case UTF8: return name + ".rawNameUtf8";
            case UTF16: return name + ".rawNameUtf16";
            default: throw new AssertionError(mode);
        }
    }

    private void emitFastjsonName(JavaWriter out, String name, FastjsonMode mode) {
        out.line("w.writeNameRaw(" + name + ");");
    }

    private void emitNativeHelper(JavaWriter out, BindingValue value, FastjsonMode mode) {
        out.beginBlock("private static void " + fastjsonHelperName(value, mode)
                + "(com.alibaba.fastjson2.JSONWriter w, " + value.type()
                + " value) throws java.io.IOException");
        out.beginBlock("if (value == null)");
        out.line("w.writeNull();");
        out.line("return;");
        out.endBlock();

        switch (value.kind()) {
            case POJO:
                out.line("w.startObject();");
                emitNativeProperties(out, value, mode);
                out.line("w.endObject();");
                break;
            case LIST:
                out.line("w.startArray();");
                out.beginBlock("if (value instanceof java.util.RandomAccess)");
                out.beginBlock("for (int i = 0, size = value.size(); i < size; i++)");
                out.beginBlock("if (i != 0)");
                out.line("w.writeComma();");
                out.endBlock();
                if (value.elementValue().kind() == BindingValue.Kind.STRING) {
                    out.line("w.writeString(value.get(i));");
                } else {
                    emitNativeValue(out, value.elementValue(), "value.get(i)", mode);
                }
                out.endBlock();
                out.endBlock(" else {");
                out.indent();
                out.line("int count = 0;");
                out.beginBlock("for (" + value.elementValue().type() + " element : value)");
                out.beginBlock("if (count++ > 0)");
                out.line("w.writeComma();");
                out.endBlock();
                if (value.elementValue().kind() == BindingValue.Kind.STRING) {
                    out.line("w.writeString(element);");
                } else {
                    emitNativeValue(out, value.elementValue(), "element", mode);
                }
                out.endBlock();
                out.endBlock();
                out.line("w.endArray();");
                break;
            case SET:
                out.line("w.startArray();");
                out.line("int count = 0;");
                out.beginBlock("for (" + value.elementValue().type() + " element : value)");
                out.beginBlock("if (count++ > 0)");
                out.line("w.writeComma();");
                out.endBlock();
                emitNativeValue(out, value.elementValue(), "element", mode);
                out.endBlock();
                out.line("w.endArray();");
                break;
            case MAP:
                out.line("w.startObject();");
                out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
                out.line("w.writeName((String) entry.getKey());");
                out.line("w.writeColon();");
                emitNativeValue(out, value.mapValue(),
                        "(" + value.mapValue().type() + ") entry.getValue()", mode);
                out.endBlock();
                out.line("w.endObject();");
                break;
            default:
                throw new AssertionError(value.kind());
        }
        out.endBlock();
    }

    private void emitNativeProperties(JavaWriter out, BindingValue value, FastjsonMode mode) {
        int index = 0;
        for (BindingProperty property : value.properties()) {
            BindingValue child = property.value();
            String field = compiledNameField(value, index++);
            String expr = access(property.access(), "value");
            if (fixedRawName(property.name())) {
                int length = property.name().length();
                String args = field + ".rawName0";
                if (length == 9) args += ", (int) " + field + ".rawName1";
                else if (length > 9) args += ", " + field + ".rawName1";
                out.line("w.writeName" + length + "Raw(" + args + ");");
            } else {
                emitFastjsonName(out, fastjsonNameExpr(field, mode), mode);
            }
            emitNativeValue(out, child, expr, mode);
        }
    }

    boolean needsFastjsonStringHelper(BindingValue value) {
        if (backend.backend() != org.sjf4j.annotation.binding.Backend.FASTJSON2
                || value.direction() != BindingPlan.Direction.WRITE_TO
                || !nativeEligible(value, new java.util.HashSet<BindingValue>())) return false;
        return containsFastjsonString(value, new java.util.HashSet<BindingValue>());
    }

    private boolean containsFastjsonString(BindingValue value,
            java.util.Set<BindingValue> visited) {
        if (!visited.add(value)) return false;
        switch (value.kind()) {
            case STRING:
                return true;
            case POJO:
                for (BindingProperty property : value.properties()) {
                    if (containsFastjsonString(property.value(), visited)) return true;
                }
                return false;
            case LIST:
                // List<String> already calls JSONWriter.writeString(String) directly.
                return value.elementValue().kind() != BindingValue.Kind.STRING
                        && containsFastjsonString(value.elementValue(), visited);
            case SET:
                return containsFastjsonString(value.elementValue(), visited);
            case MAP:
                return containsFastjsonString(value.mapValue(), visited);
            default:
                return false;
        }
    }

    void emitFastjsonStringHelper(JavaWriter out) {
        out.beginBlock("private static void _writeString(com.alibaba.fastjson2.JSONWriter w,"
                + " String value)");
        out.beginBlock("if (value == null)");
        out.line("w.writeNull();");
        out.endBlock(" else {");
        out.indent();
        out.line("w.writeString(value);");
        out.endBlock();
        out.endBlock();
    }

    private String fastjsonWriteMethod(BindingValue.Kind kind) {
        switch (kind) {
            case STRING: return "writeString";
            case BOOLEAN: return "writeBool";
            case BYTE: return "writeInt8";
            case SHORT: return "writeInt16";
            case INT: return "writeInt32";
            case LONG: return "writeInt64";
            case FLOAT: return "writeFloat";
            case DOUBLE: return "writeDouble";
            case BIG_INTEGER: return "writeBigInt";
            case BIG_DECIMAL: return "writeDecimal";
            default: throw new AssertionError(kind);
        }
    }

    private String fastjsonUnboxArgument(String type) {
        switch (type) {
            case "java.lang.Boolean": return "value.booleanValue()";
            case "java.lang.Byte": return "value.byteValue()";
            case "java.lang.Short": return "value.shortValue()";
            case "java.lang.Integer": return "value.intValue()";
            case "java.lang.Long": return "value.longValue()";
            case "java.lang.Float": return "value.floatValue()";
            case "java.lang.Double": return "value.doubleValue()";
            default: return "value";
        }
    }

    private void emitNativeValue(JavaWriter out, BindingValue value,
            String expression, FastjsonMode mode) {
        emitNativeValue(out, value, expression, mode, "w");
    }

    private void emitNativeValue(JavaWriter out, BindingValue value,
            String expression, FastjsonMode mode, String writer) {
        emitNativeValue(out, value, expression, mode, writer, "item");
    }

    private void emitNativeValue(JavaWriter out, BindingValue value,
            String expression, FastjsonMode mode, String writer, String item) {
        if (value.helperRequired()) {
            out.line(fastjsonHelperName(value, mode) + "(" + writer + ", " + expression + ");");
            return;
        }

        switch (value.kind()) {
            case STRING:
                out.line("_writeString(" + writer + ", " + expression + ");");
                return;
            case CHARACTER:
                if (value.primitive()) {
                    out.line(writer + ".writeString(Character.toString(" + expression + "));");
                } else {
                    out.beginBlock("");
                    out.line("Character " + item + " = " + expression + ";");
                    out.beginBlock("if (" + item + " == null)");
                    out.line(writer + ".writeNull();");
                    out.endBlock(" else {");
                    out.indent();
                    out.line(writer + ".writeString(" + item + ".toString());");
                    out.endBlock();
                    out.endBlock();
                }
                return;
            case ENUM:
                out.beginBlock("");
                out.line(value.type() + " " + item + " = " + expression + ";");
                out.beginBlock("if (" + item + " == null)");
                out.line(writer + ".writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line(writer + ".writeString(" + item + ".name());");
                out.endBlock();
                out.endBlock();
                return;
            default:
                break;
        }

        String method = fastjsonWriteMethod(value.kind());
        if (value.primitive()) {
            out.line(writer + "." + method + "(" + expression + ");");
        } else {
            out.beginBlock("");
            out.line(value.type() + " " + item + " = " + expression + ";");
            out.beginBlock("if (" + item + " == null)");
            out.line(writer + ".writeNull();");
            out.endBlock(" else {");
            out.indent();
            String arg = fastjsonUnboxArgument(value.type().toString()).replace("value", item);
            out.line(writer + "." + method + "(" + arg + ");");
            out.endBlock();
            out.endBlock();
        }
    }

    private String access(
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
