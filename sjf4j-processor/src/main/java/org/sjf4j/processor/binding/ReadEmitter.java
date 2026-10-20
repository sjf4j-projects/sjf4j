package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

import javax.lang.model.element.VariableElement;

/** Emits direct concrete-reader binding code. */
final class ReadEmitter {

    private static final String STREAMING_IO =
            "org.sjf4j.binding.StreamingIO";

    private static final String NAME_MATCHER =
            "org.sjf4j.binding.NameMatcher";

    private final ProcessorContext context;
    private final BackendSpec backend;
    private final Fastjson2PojoReadEmitter fastjson2Pojo;

    ReadEmitter(
            ProcessorContext context,
            BackendSpec backend) {

        this.context = context;
        this.backend = backend;
        this.fastjson2Pojo = backend.backend()
                == Backend.FASTJSON2
                ? new Fastjson2PojoReadEmitter() : null;
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
                (value.kind() != BindingValue.Kind.POJO && value.kind() != BindingValue.Kind.JOJO) ||
                value.properties().isEmpty()) {
            return;
        }

        StringBuilder line = new StringBuilder();
        line.append("private static final ")
                .append(NAME_MATCHER)
                .append(' ')
                .append(matcherFieldName(value))
                .append(" = new ")
                .append(backend.matcherType())
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

    void emitHelper(
            JavaWriter out,
            BindingValue value) {

        out.beginBlock(
                "private " +
                        value.type() +
                        " " +
                        value.helperName() +
                        "(" +
                        backend.readerType() +
                        " reader) throws java.io.IOException");

        switch (value.kind()) {
            case POJO:
            case JOJO:
                emitPojo(out, value);
                break;
            case JSON_OBJECT:
                out.line("Object raw = reader.readRaw();");
                out.line("return raw == null ? null : new org.sjf4j.JsonObject("
                        + STREAMING_IO + ".castMap(raw));");
                break;
            case JSON_ARRAY:
                out.line("Object raw = reader.readRaw();");
                out.line("return raw == null ? null : new org.sjf4j.JsonArray("
                        + STREAMING_IO + ".castList(raw));");
                break;

            case ARRAY:
                emitArray(out, value);
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
        if (fastjson2Pojo != null && value.creator() == null
                && (value.kind() == BindingValue.Kind.POJO || value.kind() == BindingValue.Kind.JOJO)
                && !value.properties().isEmpty()) {
            out.blank();
            fastjson2Pojo.emitFallback(out, value, this);
        }
    }

    private void emitPojo(
            JavaWriter out,
            BindingValue value) {

        if (value.creator() != null) {
            emitCreator(out, value);
            return;
        }

        emitObjectStart(out);

        out.line(
                value.type() +
                        " value = new " +
                        value.type() +
                        "();");

        if (fastjson2Pojo != null && !value.properties().isEmpty()) {
            fastjson2Pojo.emitProperties(out, value, this, false);
        } else if (backend.usesNameMatcher() && !value.properties().isEmpty()) {
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

        if (fastjson2Pojo == null || value.properties().isEmpty()) {
            out.endBlock();
        }
        out.line("return value;");
    }

    /** Compiled creator binding: typed arguments and deferred writable fields. */
    private void emitCreator(JavaWriter out, BindingValue value) {
        emitObjectStart(out);

        int argCount = value.creator().parameterTypes().size();
        int pendingCount = 0;
        for (BindingProperty property : value.properties()) {
            if (property.creatorIndex() >= 0) {
                out.line(property.value().type() + " arg" + property.creatorIndex() + " = "
                        + (property.value().primitive() ? primitiveDefault(property.value()) : "null") + ";");
            } else if (property.pendingIndex() >= 0) {
                int index = property.pendingIndex();
                pendingCount++;
                out.line(property.value().type() + " pending" + index + " = "
                        + (property.value().primitive() ? primitiveDefault(property.value()) : "null") + ";");
                out.line("boolean pending" + index + "Present = false;");
            }
        }
        if (argCount != 0) {
            out.line(argCount <= 64 ? "long seen = 0L;"
                    : "boolean[] seen = new boolean[" + argCount + "];");
        }

        int size = value.properties().size();
        if (fastjson2Pojo != null && size != 0) {
            fastjson2Pojo.emitProperties(out, value, this, true);
        } else if (backend.usesNameMatcher() && size != 0) {
            out.line("int nameIndex;");
            if (backend.usesExpectedNameMatch()) out.line("int expectedNameIndex = 0;");
            out.beginBlock("while ((nameIndex = reader.nextNameMatch(" + matcherFieldName(value)
                    + (backend.usesExpectedNameMatch() ? ", expectedNameIndex" : "")
                    + ")) != " + NAME_MATCHER + ".OBJECT_END)");
            emitMatchedPojoProperty(out, value);
        } else {
            out.line("String name;");
            out.beginBlock("while ((name = reader.nextName()) != null)");
            emitStringPojoProperty(out, value);
        }
        if (fastjson2Pojo == null || size == 0) out.endBlock();

        StringBuilder creation;
        switch (value.creator().kind()) {
            case CONSTRUCTOR:
                creation = new StringBuilder("new ").append(value.type()).append('(');
                break;
            case STATIC_FACTORY:
                creation = new StringBuilder(value.creator().executable().getEnclosingElement().toString())
                        .append('.').append(value.creator().executable().getSimpleName()).append('(');
                break;
            default:
                throw new AssertionError(value.creator().kind());
        }
        for (int i = 0; i < argCount; i++) {
            if (i != 0) creation.append(", ");
            creation.append("arg").append(i);
        }
        creation.append(')');
        if (pendingCount == 0) {
            out.line("return " + creation + ";");
            return;
        }

        out.line(value.type() + " value = " + creation + ";");
        for (BindingProperty property : value.properties()) {
            if (property.pendingIndex() < 0) continue;
            int index = property.pendingIndex();
            out.beginBlock("if (pending" + index + "Present)");
            out.line(assignment(property.access(), "value", "pending" + index));
            out.endBlock();
        }
        out.line("return value;");
    }

    private static String primitiveDefault(BindingValue value) {
        switch (value.kind()) {
            case BOOLEAN: return "false";
            case LONG: return "0L";
            case FLOAT: return "0f";
            case DOUBLE: return "0d";
            default: return "0";
        }
    }

    private void emitMatchedPojoProperty(
            JavaWriter out,
            BindingValue value) {
        emitMatchedPojoProperty(out, value, true);
    }

    void emitMatchedPojoProperty(JavaWriter out, BindingValue value, boolean updateExpected) {
        out.beginBlock("switch (nameIndex)");

        int index = 0;
        for (BindingProperty property : value.properties()) {
            int propertyIndex = index++;
            out.line("case " + propertyIndex + ":");
            out.indent();
            if (updateExpected && backend.usesExpectedNameMatch()) {
                out.line("expectedNameIndex = " + (propertyIndex + 1) + ";");
            }
            emitPropertyRead(out, property, "value", "reader");
            out.line("break;");
            out.dedent();
        }

        out.line("default:");
        out.indent();
        emitUnknownObjectProperty(out, value, "reader.currentName()");
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

            emitPropertyRead(out, property, "value", "reader");

            out.line("break;");
            out.dedent();

            for (String alias : property.aliases()) {
                out.line("case " + JavaWriter.stringLiteral(alias) + ":");
                out.indent();
                emitPropertyRead(out, property, "value", "reader");
                out.line("break;");
                out.dedent();
            }
        }

        out.line("default:");
        out.indent();
        emitUnknownObjectProperty(out, value, "name");
        out.line("break;");
        out.dedent();

        out.endBlock();
    }

    /** Only JOJO unknown members are dynamic; read-only declared names remain ignored. */
    void emitUnknownObjectProperty(JavaWriter out, BindingValue value, String nameExpression) {
        if (value.kind() != BindingValue.Kind.JOJO || !value.readDynamic()) {
            out.line("reader.skipNode();");
            return;
        }
        out.line("String unknownName = " + nameExpression + ";");
        if (!value.ignoredNames().isEmpty()) {
            StringBuilder condition = new StringBuilder();
            for (String name : value.ignoredNames()) {
                if (condition.length() > 0) condition.append(" || ");
                condition.append(JavaWriter.stringLiteral(name)).append(".equals(unknownName)");
            }
            out.beginBlock("if (" + condition + ")");
            out.line("reader.skipNode();");
            out.endBlock(" else {");
            out.indent();
        }
        out.line("java.util.Map<String, Object> dynamic = org.sjf4j.InternalAccess.dynamicProperties(value);");
        out.beginBlock("if (dynamic == null)");
        out.line("dynamic = new java.util.LinkedHashMap<>();");
        out.line("org.sjf4j.InternalAccess.dynamicProperties(value, dynamic);");
        out.endBlock();
        out.line("dynamic.put(unknownName, reader.readRaw());");
        if (!value.ignoredNames().isEmpty()) out.endBlock();
    }

    String matcherFieldName(BindingValue value) {
        return value.helperName() + "_names";
    }

    void emitPropertyRead(JavaWriter out, BindingProperty property,
                          String owner, String reader) {
        emitPropertyReadExpression(out, property, owner,
                readExpression(property.value(), reader));
    }

    /** Reuses the normal POJO assignment semantics for backend-native reads. */
    void emitPropertyReadExpression(JavaWriter out, BindingProperty property,
                                    String owner, String expression) {
        int index = property.creatorIndex();
        if (index < 0) {
            int pending = property.pendingIndex();
            if (pending >= 0) {
                out.line("pending" + pending + " = " + expression + ";");
                out.line("pending" + pending + "Present = true;");
            } else {
                out.line(assignment(property.access(), owner, expression));
            }
            return;
        }
        boolean wide = property.creatorCount() > 64;
        String flag = wide ? "seen[" + index + "]" : "seen";
        String condition = wide ? flag : "(seen & (1L << " + index + ")) != 0L";
        out.beginBlock("if (" + condition + ")");
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("duplicate creator argument assignment for '" + property.name() + "'")
                + ");");
        out.endBlock();
        out.line(wide ? flag + " = true;" : "seen |= (1L << " + index + ");");
        out.line("arg" + index + " = " + expression + ";");
    }

    private void emitArray(JavaWriter out, BindingValue value) {
        emitArrayStart(out);

        String allocation = value.type().toString().replaceFirst("\\[\\]", "[8]");
        out.line(value.type() + " value = new " + allocation + ";");
        out.line("int size = 0;");

        out.beginBlock("while (!reader.nextIfArrayEnd())");
        out.beginBlock("if (size == value.length)");
        out.line("value = java.util.Arrays.copyOf(value, size << 1);");
        out.endBlock();
        out.line("value[size++] = " + readExpression(value.elementValue(), "reader") + ";");
        out.endBlock();

        out.line("return size == value.length ? value : java.util.Arrays.copyOf(value, size);");
    }

    private void emitList(
            JavaWriter out,
            BindingValue value) {

        // Raw List<Object> is already handled by each backend's raw reader.
        if (context.typeUtils.erasure(value.type()).toString().equals("java.util.List")
                && context.types.isObject(value.elementValue().type())) {
            out.line("Object raw = reader.readRaw();");
            out.line("return raw == null ? null : " + STREAMING_IO + ".castList(raw);");
            return;
        }

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

        // Raw Map<String, Object> uses the same backend-independent strategy
        // as List<Object>; typed containers keep their compiled element plan.
        if (context.typeUtils.erasure(value.type()).toString().equals("java.util.Map")
                && context.types.isObject(value.mapValue().type())) {
            out.line("Object raw = reader.readRaw();");
            out.line("return raw == null ? null : " + STREAMING_IO + ".castMap(raw);");
            return;
        }

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

    /** Nullable raw readers preserve JSON null and existing ValueInfo decoding. */
    private String codecRawReader(BindingValue value) {
        if (value.codecRawType() == null) return null;
        switch (value.codecRawType().toString()) {
            case "java.lang.String": return "readString";
            case "java.lang.Boolean": return "readBoolean";
            case "java.lang.Integer": return "readInt";
            case "java.lang.Long": return "readLong";
            case "java.lang.Double": return "readDouble";
            case "java.lang.Float": return "readFloat";
            case "java.lang.Short": return "readShort";
            case "java.lang.Byte": return "readByte";
            case "java.math.BigInteger": return "readBigInteger";
            case "java.math.BigDecimal": return "readBigDecimal";
            case "java.lang.Number": return "readNumber";
            default: return null;
        }
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

            case VALUE_CODEC:
                String rawReader = codecRawReader(value);
                String runtimeRead = STREAMING_IO + ".readValueCodec(" + reader + ", "
                        + value.helperName() + ", this.context)";
                String scalarRead;
                if (rawReader == null) {
                    scalarRead = runtimeRead;
                } else {
                    String rawClass = value.codecRawType().toString();
                    scalarRead = "(" + value.helperName() + ".rawClazz == "
                            + rawClass + ".class ? " + value.helperName()
                            + ".rawToValue(" + reader + "." + rawReader + "()) : "
                            + runtimeRead + ")";
                }
                boolean autoRead = value.codecName() == null && value.codecPattern() == null;
                if (autoRead) {
                    String set = value.helperName() + "Set";
                    String selected = STREAMING_IO + ".readValueSet(" + reader + ", "
                            + set + ", this.context.defaultValueFormat("
                            + value.type() + ".class), this.context)";
                    // Only differing null/default codecs require an extra probe.
                    // Normal scalar reads retain their typed fast paths.
                    String scalarWithNull = "(" + value.helperName() + " != "
                            + value.helperName() + "Null && " + reader + ".nextIfNull() ? "
                            + value.helperName() + "Null.rawToValue(null) : " + scalarRead + ")";
                    return "(" + value.type() + ") (" + set + ".multipleJsonTypes ? "
                            + selected + " : " + scalarWithNull + ")";
                }
                return "(" + value.type() + ") " + scalarRead;

            case ENUM:
                // Keep string, ordinal and null semantics aligned with StreamingIO.
                return "(" + value.type() + ") " +
                        STREAMING_IO +
                        ".readEnum(" + reader + ", " +
                        value.type() + ".class, " +
                        "this.context)";

            case ONE_OF:
                return "(" + value.type() + ") org.sjf4j.binding.OneOfIO"
                        + ".readOneOf(" + reader + ", " + value.helperName() + ", this.context)";

            case RUNTIME:
                if (context.types.isObject(value.type())) {
                    return "(" + value.type() + ") " +
                            reader + ".readRaw()";
                }
                return "(" + value.type() + ") " +
                        STREAMING_IO +
                        ".readNode(" +
                        reader + ", " +
                        context.typeUtils
                                .erasure(value.type()) +
                        ".class, " +
                        "this.context)";

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

        for (VariableElement parameter :
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
