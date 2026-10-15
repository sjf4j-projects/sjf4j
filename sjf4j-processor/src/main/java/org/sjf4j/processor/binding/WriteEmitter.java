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
            if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2) {
                out.line("private static final byte[] " + field + "_rawUtf8 = " +
                        "(com.alibaba.fastjson2.JSON.toJSONString(" +
                        JavaWriter.stringLiteral(property.name()) + ") + \":\")" +
                        ".getBytes(java.nio.charset.StandardCharsets.UTF_8);");
            }
        }
    }

    private String compiledNameField(BindingValue value, int index) {
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

        NameAllocator names =
                methodNames(plan);

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
            case FASTJSON2:
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

        emitWriteBody(
                out,
                value,
                source,
                writer);

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

        if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2) {
            String writer = names.newName("writer");
            out.beginBlock("try (" + backend.writerType() + " " + writer
                    + " = this.binder.createWriterUtf8())");
            if (value.helperRequired() && nativeEligible(value,
                    new java.util.HashSet<BindingValue>())) {
                out.beginBlock("if (" + writer + ".supportsRawUtf8Names())");
                out.line(writer + ".startDocument();");
                out.line(value.helperName() + "(" + writer
                        + ", " + source + ");");
                out.line(writer + ".endDocument();");
                out.line(writer + ".flush();");
                out.endBlock(" else {");
                out.indent();
                emitWriteBody(out, value, source, writer);
                out.endBlock();
            } else {
                emitWriteBody(out, value, source, writer);
            }
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

        emitWriteBody(
                out,
                value,
                source,
                writer);

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

        emitWriteBody(
                out,
                value,
                source,
                writer);

    }

    private void emitWriteBody(
            JavaWriter out,
            BindingValue value,
            String source,
            String writer) {

        out.line(writer + ".startDocument();");

        writeValue(
                out,
                value,
                writer,
                source);

        out.line(writer + ".endDocument();");
        out.line(writer + ".flush();");
    }

    void emitHelper(
            JavaWriter out,
            BindingValue value) {

        out.beginBlock(
                "private static void " +
                        value.helperName() +
                        "(" +
                        backend.writerType() +
                        " writer, " +
                        value.type() +
                        " value) throws java.io.IOException");

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
                throw new AssertionError(
                        "No write helper for " +
                                value.kind());
        }

        out.endBlock();

        if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2
                && nativeEligible(value, new java.util.HashSet<BindingValue>())) {
            emitNativeHelper(out, value);
        }
    }

    private void emitPojo(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("writer.startObject();");

        // Reuse the native property emitter directly inside the normal POJO
        // helper. This keeps its lifecycle, recursion and fallback unchanged.
        if (backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2
                && nativeEligible(value, new java.util.HashSet<BindingValue>())) {
            out.beginBlock("if (writer.supportsRawUtf8Names())");
            out.line(value.helperName() + "Properties(writer.nativeWriter(), value);");
            out.line("writer.endObject();");
            out.line("return;");
            out.endBlock();
        }

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

    private void emitNativeHelper(JavaWriter out, BindingValue value) {
        String name = value.helperName() + "Native";
        String type = "com.alibaba.fastjson2.JSONWriter";
        out.beginBlock("private static void " + name + "(" + type
                + " w, " + value.type() + " value)");
        out.beginBlock("if (value == null)");
        out.line("w.writeNull();");
        out.line("return;");
        out.endBlock();
        switch (value.kind()) {
            case POJO:
                out.line("w.startObject();");
                out.line(value.helperName() + "Properties(w, value);");
                out.line("w.endObject();");
                break;
            case LIST:
            case SET:
                out.line("w.startArray();");
                out.line("int count = 0;");
                out.beginBlock("for (" + value.elementValue().type()
                        + " element : value)");
                out.beginBlock("if (count++ > 0)");
                out.line("w.writeComma();");
                out.endBlock();
                emitNativeValue(out, value.elementValue(), "element");
                out.endBlock();
                out.line("w.endArray();");
                break;
            case MAP:
                out.line("w.startObject();");
                out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
                out.line("w.writeName((String) entry.getKey());");
                out.line("w.writeColon();");
                emitNativeValue(out, value.mapValue(),
                        "(" + value.mapValue().type() + ") entry.getValue()");
                out.endBlock();
                out.line("w.endObject();");
                break;
            default:
                throw new AssertionError(value.kind());
        }
        out.endBlock();
        if (value.kind() == BindingValue.Kind.POJO) {
            out.beginBlock("private static void " + value.helperName()
                    + "Properties(" + type + " w, " + value.type() + " value)");
            int index = 0;
            for (BindingProperty property : value.properties()) {
                out.line("w.writeNameRaw(" + compiledNameField(value, index++)
                        + "_rawUtf8);");
                emitNativeValue(out, property.value(),
                        access(property.access(), "value"));
            }
            out.endBlock();
        }
    }

    private void emitNativeValue(JavaWriter out, BindingValue value,
            String expression) {
        if (value.helperRequired()) {
            out.line(value.helperName() + "Native(w, " + expression + ");");
            return;
        }
        String method;
        switch (value.kind()) {
            case STRING:
                out.beginBlock("if (" + expression + " == null)");
                out.line("w.writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line("w.writeString(" + expression + ");");
                out.endBlock();
                return;
            case BOOLEAN: method = "writeBool"; break;
            case BYTE: method = "writeInt8"; break;
            case SHORT: method = "writeInt16"; break;
            case INT: method = "writeInt32"; break;
            case LONG: method = "writeInt64"; break;
            case FLOAT: method = "writeFloat"; break;
            case DOUBLE: method = "writeDouble"; break;
            case CHARACTER:
                if (!value.primitive()) {
                    out.beginBlock("if (" + expression + " == null)");
                    out.line("w.writeNull();");
                    out.endBlock(" else {");
                    out.indent();
                }
                out.line("w.writeString(Character.toString(" + expression + "));");
                if (!value.primitive()) out.endBlock();
                return;
            case ENUM:
                out.beginBlock("if (" + expression + " == null)");
                out.line("w.writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line("w.writeString(" + expression + ".name());");
                out.endBlock();
                return;
            case BIG_INTEGER: method = "writeBigInt"; break;
            case BIG_DECIMAL: method = "writeDecimal"; break;
            default: throw new AssertionError(value.kind());
        }
        if (!value.primitive()) {
            out.beginBlock("if (" + expression + " == null)");
            out.line("w.writeNull();");
            out.endBlock(" else {");
            out.indent();
        }
        out.line("w." + method + "(" + expression + ");");
        if (!value.primitive()) out.endBlock();
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
