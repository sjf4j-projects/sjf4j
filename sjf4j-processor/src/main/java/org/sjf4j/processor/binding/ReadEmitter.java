package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

/** Emits direct concrete-reader binding code. */
final class ReadEmitter {

    private static final String STREAMING_IO =
            "org.sjf4j.binding.StreamingIO";

    private static final String RUNTIME_CONTEXT =
            "org.sjf4j.RuntimeContext";

    private static final String NAME_MATCHER =
            "org.sjf4j.binding.NameMatcher";

    private final ProcessorContext context;
    private final BackendSpec backend;

    ReadEmitter(
            ProcessorContext context,
            BackendSpec backend) {

        this.context = context;
        this.backend = backend;
    }

    boolean usesNameMatcher() {
        return backend.usesNameMatcher();
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

    void emitNameMatcherField(
            JavaWriter out,
            BindingValue value) {

        if (!backend.usesNameMatcher() ||
                value.kind() != BindingValue.Kind.POJO ||
                value.properties().isEmpty()) {
            return;
        }

        StringBuilder line = new StringBuilder();
        line.append("private static final ")
                .append(NAME_MATCHER)
                .append(' ')
                .append(matcherFieldName(value))
                .append(" = ")
                .append(backend.readerType())
                .append(".compiledNameMatcher(");

        for (int i = 0; i < value.properties().size(); i++) {
            if (i > 0) {
                line.append(", ");
            }
            line.append(JavaWriter.stringLiteral(
                    value.properties().get(i).name()));
        }

        line.append(");");
        out.line(line.toString());
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

        if (backend.usesNameMatcher() && !value.properties().isEmpty()) {
            out.line("int nameIndex;");
            if (backend.usesExpectedNameMatch()) {
                out.line("int expectedNameIndex = 0;");
            }

            out.beginBlock(
                    "while ((nameIndex = reader.nextNameMatch(" +
                            matcherFieldName(value) +
                            (backend.usesExpectedNameMatch()
                                    ? ", expectedNameIndex"
                                    : "") +
                            ")) != " + NAME_MATCHER + ".OBJECT_END)");

            emitMatchedPojoProperty(out, value);
        } else {
            out.line("String name;");
            out.beginBlock(
                    "while ((name = reader.nextName()) != null)");

            emitStringPojoProperty(out, value);
        }

        out.endBlock();
        out.line("return value;");
    }

    private void emitMatchedPojoProperty(
            JavaWriter out,
            BindingValue value) {

        out.beginBlock("switch (nameIndex)");

        int index = 0;
        for (BindingProperty property : value.properties()) {
            int propertyIndex = index++;
            out.line("case " + propertyIndex + ":");
            out.indent();
            if (backend.usesExpectedNameMatch()) {
                out.line("expectedNameIndex = " + (propertyIndex + 1) + ";");
            }
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
        if (backend.usesExpectedNameMatch()) {
            out.line("expectedNameIndex = -1;");
        }
        out.line("reader.skipNode();");
        out.line("break;");
        out.dedent();

        out.endBlock();
    }

    private void emitStringPojoProperty(
            JavaWriter out,
            BindingValue value) {

        out.beginBlock("switch (name)");

        for (BindingProperty property : value.properties()) {
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
        out.line("reader.skipNode();");
        out.line("break;");
        out.dedent();

        out.endBlock();
    }

    private String matcherFieldName(BindingValue value) {
        return value.helperName() + "_names";
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

        out.line("String name;");
        out.beginBlock(
                "while ((name = reader.nextName()) != null)");

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
                return reader + ".readString()";

            case CHARACTER:
                return value.primitive()
                        ? reader + ".readCharValue()"
                        : reader + ".readChar()";

            case BOOLEAN:
                return value.primitive()
                        ? reader + ".readBooleanValue()"
                        : reader + ".readBoolean()";

            case BYTE:
                return value.primitive()
                        ? reader + ".readByteValue()"
                        : reader + ".readByte()";

            case SHORT:
                return value.primitive()
                        ? reader + ".readShortValue()"
                        : reader + ".readShort()";

            case INT:
                return value.primitive()
                        ? reader + ".readIntValue()"
                        : reader + ".readInt()";

            case LONG:
                return value.primitive()
                        ? reader + ".readLongValue()"
                        : reader + ".readLong()";

            case FLOAT:
                return value.primitive()
                        ? reader + ".readFloatValue()"
                        : reader + ".readFloat()";

            case DOUBLE:
                return value.primitive()
                        ? reader + ".readDoubleValue()"
                        : reader + ".readDouble()";

            case NUMBER:
                return reader + ".readNumber()";

            case BIG_INTEGER:
                return reader + ".readBigInteger()";

            case BIG_DECIMAL:
                return reader + ".readBigDecimal()";

            case ENUM:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        value.type() +
                        ".valueOf(" +
                        reader +
                        ".readString()))";

            case RUNTIME:
                if (context.types.isObject(value.type())) {
                    return "(" + value.type() + ") " +
                            reader + ".readRawNode()";
                }
                return "(" + value.type() + ") " +
                        STREAMING_IO +
                        ".readNode(" +
                        reader + ", " +
                        context.typeUtils
                                .erasure(value.type()) +
                        ".class, " +
                        RUNTIME_CONTEXT +
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
