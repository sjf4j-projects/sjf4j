package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.property.PropertyAccess;

/** Emits direct StreamingWriter binding code. */
final class WriteEmitter {

    private static final String WRITER =
            "org.sjf4j.binding.StreamingWriter";

    private static final String STREAMING_IO =
            "org.sjf4j.binding.StreamingIO";

    private static final String STREAMING_CONTEXT =
            "org.sjf4j.binding.StreamingContext";

    void emitMethod(
            JavaWriter out,
            BindingCompiler.CompiledMethod compiled) {

        BindingPlan plan = compiled.plan();

        BindingSource.beginOverride(
                out,
                plan.method());

        String writer =
                BindingSource.parameterName(
                        plan,
                        plan.readerWriterParameter());

        String source =
                BindingSource.parameterName(
                        plan,
                        plan.valueParameter());

        out.line(writer + ".startDocument();");

        writeValue(
                out,
                compiled.value(),
                writer,
                source);

        out.line(writer + ".endDocument();");

        BindingSource.endOverride(out);
    }

    void emitHelper(
            JavaWriter out,
            BindingValue value) {

        out.beginBlock(
                "private static void " +
                        value.helperName() +
                        "(" + WRITER +
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
    }

    private void emitPojo(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("writer.startObject();");

        int index = 0;

        for (BindingProperty property :
                value.properties()) {

            if (index++ > 0) {
                out.line("writer.separateProperty();");
            }

            out.line(
                    "writer.writeName(" +
                            JavaWriter.stringLiteral(
                                    property.name()) +
                            ");");

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
                if (value.primitive()) {
                    out.line(
                            writer +
                                    ".writeCharValue(" +
                                    expression +
                                    ");");
                } else {
                    out.beginBlock(
                            "if (" + expression +
                                    " == null)");
                    out.line(writer + ".writeNull();");
                    out.endBlock(" else {");
                    out.indent();
                    out.line(
                            writer +
                                    ".writeCharValue(" +
                                    expression +
                                    ".charValue());");
                    out.endBlock();
                }
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

            case FALLBACK:
                out.line(
                        STREAMING_IO +
                                ".writeNode(" +
                                writer + ", " +
                                expression + ", " +
                                STREAMING_CONTEXT +
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
}
