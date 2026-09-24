package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

/** Emits direct concrete-reader binding code. */
final class ReadEmitter {

    private static final String STREAMING_IO =
            "org.sjf4j.binding.StreamingIO";

    private static final String STREAMING_CONTEXT =
            "org.sjf4j.binding.StreamingContext";

    private final ProcessorContext context;
    private final BackendSpec backend;

    ReadEmitter(
            ProcessorContext context,
            BackendSpec backend) {

        this.context = context;
        this.backend = backend;
    }

    void emitMethod(
            JavaWriter out,
            BindingCompiler.CompiledMethod compiled) {

        BindingPlan plan = compiled.plan();
        BindingValue value = compiled.value();

        BindingSource.beginOverride(
                out,
                plan.method());

        String input =
                BindingSource.parameterName(
                        plan,
                        0);

        NameAllocator names =
                methodNames(plan);

        String reader =
                names.newName("reader");

        boolean owned =
                plan.readInput() == BindingPlan.ReadInput.STRING ||
                plan.readInput() == BindingPlan.ReadInput.BYTES;

        if (owned) {
            out.beginBlock(
                    "try (" +
                            backend.readerType() +
                            " " + reader +
                            " = this.binder.createReader(" +
                            input + "))");

            emitReadBody(
                    out,
                    plan,
                    value,
                    reader,
                    names);

            out.endBlock();
        } else {
            out.line(
                    backend.readerType() +
                            " " + reader +
                            " = this.binder.createReader(" +
                            input + ");");

            emitReadBody(
                    out,
                    plan,
                    value,
                    reader,
                    names);
        }

        BindingSource.endOverride(out);
    }

    private void emitReadBody(
            JavaWriter out,
            BindingPlan plan,
            BindingValue value,
            String reader,
            NameAllocator names) {

        String result =
                names.newName("result");

        out.line(reader + ".startDocument();");

        out.line(
                plan.valueType() +
                        " " + result +
                        " = " +
                        readExpression(
                                value,
                                reader) +
                        ";");

        out.line(reader + ".endDocument();");
        out.line("return " + result + ";");
    }

    void emitHelper(
            JavaWriter out,
            BindingValue value) {

        out.beginBlock(
                "private static " +
                        value.type() +
                        " " +
                        value.helperName() +
                        "(" +
                        backend.readerType() +
                        " reader) throws java.io.IOException");

        switch (value.kind()) {
            case POJO:
                emitPojo(out, value);
                break;

            case LIST:
                emitList(out, value);
                break;

            case SET:
                emitSet(out, value);
                break;

            case MAP:
                emitMap(out, value);
                break;

            default:
                throw new AssertionError(
                        "No read helper for " +
                                value.kind());
        }

        out.endBlock();
    }

    private void emitPojo(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startObject();");

        out.line(
                value.type() +
                        " value = new " +
                        value.type() +
                        "();");

        out.beginBlock(
                "while (!reader.nextIfObjectEnd())");

        out.line("String name = reader.nextName();");
        out.beginBlock("switch (name)");

        for (BindingProperty property :
                value.properties()) {

            out.line(
                    "case " +
                            JavaWriter.stringLiteral(
                                    property.name()) +
                            ":");

            out.indent();

            out.line(
                    assignment(
                            property.access(),
                            "value",
                            readExpression(
                                    property.value(),
                                    "reader")));

            out.line("break;");
            out.dedent();
        }

        out.line("default:");
        out.indent();
        out.line("reader.skipNext();");
        out.line("break;");
        out.dedent();

        out.endBlock();
        out.endBlock();

        out.line("return value;");
    }

    private void emitList(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startArray();");

        out.line(
                value.type() +
                        " value = new java.util.ArrayList<>();");

        out.beginBlock(
                "while (!reader.nextIfArrayEnd())");

        out.line(
                "value.add(" +
                        readExpression(
                                value.elementValue(),
                                "reader") +
                        ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitSet(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startArray();");

        out.line(
                value.type() +
                        " value = new java.util.LinkedHashSet<>();");

        out.beginBlock(
                "while (!reader.nextIfArrayEnd())");

        out.line(
                "value.add(" +
                        readExpression(
                                value.elementValue(),
                                "reader") +
                        ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitMap(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startObject();");

        out.line(
                value.type() +
                        " value = new java.util.LinkedHashMap<>();");

        out.beginBlock(
                "while (!reader.nextIfObjectEnd())");

        out.line("String name = reader.nextName();");

        out.line(
                "value.put(name, " +
                        readExpression(
                                value.mapValue(),
                                "reader") +
                        ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitNullReturn(JavaWriter out) {
        out.beginBlock("if (reader.nextIfNull())");
        out.line("return null;");
        out.endBlock();
    }

    private String readExpression(
            BindingValue value,
            String reader) {

        if (value.helperRequired()) {
            return value.helperName() +
                    "(" + reader + ")";
        }

        switch (value.kind()) {
            case STRING:
                return reader + ".nextStringOrNull()";

            case CHARACTER:
                if (value.primitive()) {
                    return reader + ".nextCharValue()";
                }
                return "(" + reader +
                        ".nextIfNull() ? null : java.lang.Character.valueOf(" +
                        reader + ".nextCharValue()))";

            case BOOLEAN:
                return value.primitive()
                        ? reader + ".nextBooleanValue()"
                        : reader + ".nextBoolean()";

            case BYTE:
                return value.primitive()
                        ? reader + ".nextByteValue()"
                        : reader + ".nextByte()";

            case SHORT:
                return value.primitive()
                        ? reader + ".nextShortValue()"
                        : reader + ".nextShort()";

            case INT:
                return value.primitive()
                        ? reader + ".nextIntValue()"
                        : reader + ".nextInt()";

            case LONG:
                return value.primitive()
                        ? reader + ".nextLongValue()"
                        : reader + ".nextLong()";

            case FLOAT:
                return value.primitive()
                        ? reader + ".nextFloatValue()"
                        : reader + ".nextFloat()";

            case DOUBLE:
                return value.primitive()
                        ? reader + ".nextDoubleValue()"
                        : reader + ".nextDouble()";

            case NUMBER:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        reader + ".nextNumber())";

            case BIG_INTEGER:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        reader + ".nextBigInteger())";

            case BIG_DECIMAL:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        reader + ".nextBigDecimal())";

            case ENUM:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        value.type() +
                        ".valueOf(" +
                        reader +
                        ".nextString()))";

            case RUNTIME:
                return "(" + value.type() + ") " +
                        STREAMING_IO +
                        ".readNode(" +
                        reader + ", " +
                        context.typeUtils
                                .erasure(value.type()) +
                        ".class, " +
                        STREAMING_CONTEXT +
                        ".EMPTY)";

            default:
                throw new AssertionError(
                        "Unsupported read kind " +
                                value.kind());
        }
    }

    private String assignment(
            PropertyAccess access,
            String owner,
            String expression) {

        if (access.isMethod()) {
            return owner + '.' +
                    access.memberName() +
                    '(' + expression +
                    ");";
        }

        return owner + '.' +
                access.memberName() +
                " = " + expression +
                ";";
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
