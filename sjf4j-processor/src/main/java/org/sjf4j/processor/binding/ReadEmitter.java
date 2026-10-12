package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    boolean generatesFastjson2Matcher() {
        return backend.backend() == org.sjf4j.annotation.binding.Backend.FASTJSON2;
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
        String matcherType = generatesFastjson2Matcher()
                ? matcherClassName(value) : backend.matcherType();
        line.append("private static final ")
                .append(matcherType)
                .append(' ')
                .append(matcherFieldName(value))
                .append(" = new ")
                .append(matcherType)
                .append("(new String[]{");

        for (int i = 0; i < value.properties().size(); i++) {
            if (i > 0) line.append(", ");
            line.append(JavaWriter.stringLiteral(value.properties().get(i).name()));
        }
        line.append("}, new String[][]{");
        for (int i = 0; i < value.properties().size(); i++) {
            if (i > 0) line.append(", ");
            line.append('{');
            String[] aliases = value.properties().get(i).aliases();
            for (int j = 0; j < aliases.length; j++) {
                if (j > 0) line.append(", ");
                line.append(JavaWriter.stringLiteral(aliases[j]));
            }
            line.append('}');
        }
        line.append("});");
        out.line(line.toString());
    }

    /**
     * Generates the Fastjson2 raw-name dispatch for one concrete compiled
     * POJO plan. The surrounding property read loop remains backend-generic.
     */
    void emitFastjson2MatcherClass(JavaWriter out, BindingValue value) {
        out.beginBlock("private static final class " + matcherClassName(value)
                + " extends org.sjf4j.backend.fastjson2.binding.Fastjson2NameMatcher");
        out.beginBlock("private " + matcherClassName(value)
                + "(String[] names, String[][] aliases)");
        out.line("super(names, aliases);");
        out.endBlock();
        out.blank();

        out.line("@Override");
        out.beginBlock("protected int matchRaw(com.alibaba.fastjson2.JSONReader raw, int expectedIndex)");
        out.beginBlock("if (java.nio.ByteOrder.nativeOrder() != java.nio.ByteOrder.LITTLE_ENDIAN)");
        out.line("return UNKNOWN;");
        out.endBlock();

        Map<Integer, List<Integer>> groups = new LinkedHashMap<Integer, List<Integer>>();
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < value.properties().size(); i++) {
            String name = value.properties().get(i).name();
            names.add(name);
            if (!canMatchRaw(name)) continue;
            String literal = "\"" + name + "\":";
            int prefix = (int) packRaw(literal, 0, 4);
            List<Integer> matches = groups.get(prefix);
            if (matches == null) {
                matches = new ArrayList<Integer>();
                groups.put(prefix, matches);
            }
            matches.add(i);
        }

        if (!groups.isEmpty()) {
            out.beginBlock("switch (raw.getRawInt())");
            for (Map.Entry<Integer, List<Integer>> group : groups.entrySet()) {
                out.line("case 0x" + Integer.toHexString(group.getKey()) + ":");
                out.indent();
                for (Integer index : group.getValue()) {
                    out.beginBlock("if (" + rawCall(names.get(index)) + ")");
                    out.line("return " + index + ";");
                    out.endBlock();
                }
                out.line("break;");
                out.dedent();
            }
            out.line("default:");
            out.indent();
            out.line("break;");
            out.dedent();
            out.endBlock();
        }
        out.line("return UNKNOWN;");
        out.endBlock();
        out.endBlock();
    }

    private static boolean canMatchRaw(String name) {
        int length = name.length();
        if (length < 2 || length > 11) return false;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c >= 0x7f || c == '"' || c == '\\') return false;
        }
        return true;
    }

    private static long packRaw(String text, int start, int count) {
        long bits = 0;
        for (int i = 0; i < count; i++) {
            bits |= (long) text.charAt(start + i) << (i * 8);
        }
        return bits;
    }

    private static String rawCall(String name) {
        int length = name.length();
        String literal = "\"" + name + "\":";
        long suffix = packRaw(literal, 4, Math.min(8, literal.length() - 4));
        String fn = "raw.nextIfName4Match" + length + "(";
        switch (length) {
            case 2:
            case 3:
                return fn + ")";
            case 4:
                return fn + "(byte) 0x" + Long.toHexString(suffix & 0xffL) + ")";
            case 5:
            case 6:
            case 7:
                return fn + "0x" + Long.toHexString(suffix & 0xffffffffL) + ")";
            case 8:
                return fn + "0x" + Long.toHexString(suffix & 0xffffffffL)
                        + ", (byte) 0x" + Long.toHexString(suffix >>> 32) + ")";
            case 9:
            case 10:
            case 11:
                return fn + "0x" + Long.toHexString(suffix) + "L)";
            default:
                throw new AssertionError("Unsupported raw name length: " + length);
        }
    }

    private static String matcherClassName(BindingValue value) {
        return value.helperName() + "_Matcher";
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

        emitObjectStart(out);

        out.line(
                value.type() +
                        " value = new " +
                        value.type() +
                        "();");

        if (backend.usesNameMatcher() && !value.properties().isEmpty()) {
            out.line("int nameIndex;");
            if (backend.usesExpectedNameMatch() && !generatesFastjson2Matcher()) {
                out.line("int expectedNameIndex = 0;");
            }

            String nameMatch = generatesFastjson2Matcher()
                    ? matcherFieldName(value) + ".nextNameMatch(reader)"
                    : "reader.nextNameMatch(" + matcherFieldName(value)
                            + (backend.usesExpectedNameMatch()
                                    ? ", expectedNameIndex" : "") + ")";
            out.beginBlock("while ((nameIndex = " + nameMatch
                    + ") != " + NAME_MATCHER + ".OBJECT_END)");

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
            if (backend.usesExpectedNameMatch() && !generatesFastjson2Matcher()) {
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

            for (String alias : property.aliases()) {
                out.line("case " + JavaWriter.stringLiteral(alias) + ":");
                out.indent();
                out.line(assignment(property.access(), "value",
                        readExpression(property.value(), "reader")));
                out.line("break;");
                out.dedent();
            }
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

        emitArrayStart(out);

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

        emitArrayStart(out);

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

        emitObjectStart(out);

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

    private void emitObjectStart(JavaWriter out) {
        out.beginBlock("if (!reader.nextIfObjectStart())");
        out.beginBlock("if (reader.nextIfNull())");
        out.line("return null;");
        out.endBlock();
        out.line("reader.startObject();");
        out.endBlock();
    }

    private void emitArrayStart(JavaWriter out) {
        out.beginBlock("if (!reader.nextIfArrayStart())");
        out.beginBlock("if (reader.nextIfNull())");
        out.line("return null;");
        out.endBlock();
        out.line("reader.startArray();");
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
                // Keep string, ordinal and null semantics aligned with StreamingIO.
                return "(" + value.type() + ") " +
                        STREAMING_IO +
                        ".readEnum(" + reader + ", " +
                        value.type() + ".class, " +
                        RUNTIME_CONTEXT + ".EMPTY)";

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
