package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;
import org.sjf4j.processor.property.PropertyAccess;

import javax.lang.model.element.VariableElement;

/** Emits direct concrete-parser binding code. */
final class ReadEmitter {

    private static final String STREAMING_IO = "org.sjf4j.binding.StreamingIO";
    private static final String NAME_MATCHER = "org.sjf4j.binding.NameMatcher";

    private final ProcessorContext context;
    private final BackendSpec backend;
    private final Fastjson2PojoReadEmitter fastjson2Pojo;

    ReadEmitter(ProcessorContext context, BackendSpec backend) {
        this.context = context;
        this.backend = backend;
        this.fastjson2Pojo = backend.backend() == Backend.FASTJSON2
                ? new Fastjson2PojoReadEmitter() : null;
    }

    boolean usesNameMatcher() {
        return backend.usesNameMatcher();
    }

    void emitMethod(JavaWriter out, BindingCompiler.CompiledMethod compiled) {
        BindingPlan plan = compiled.plan();
        BindingValue value = compiled.value();

        BindingSource.beginOverride(out, plan.method());
        String input = BindingSource.parameterName(plan, 0);
        NameAllocator names = methodNames(plan);
        String parser = names.newName("parser");

        out.beginBlock("try (" + backend.parserType() + " " + parser
                + " = this.binder.createParser(" + input + "))");
        emitReadBody(out, plan, value, parser, names);
        out.endBlock();

        BindingSource.endOverride(out);
    }

    private void emitReadBody(JavaWriter out, BindingPlan plan, BindingValue value,
                              String parser, NameAllocator names) {
        String result = names.newName("result");

        out.line(parser + ".start();");

        out.line(plan.valueType() + " " + result + " = " + readExpression(value, parser) + ";");

        out.line(parser + ".end();");
        out.line("return " + result + ";");
    }

    void emitNameMatcherField(JavaWriter out, BindingValue value) {
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

    void emitHelper(JavaWriter out, BindingValue value) {
        out.beginBlock("private " + value.type() + " " + value.helperName()
                + "(" + backend.parserType() + " parser) throws java.io.IOException");

        switch (value.kind()) {
            case POJO:
            case JOJO:
                emitPojo(out, value);
                break;
            case JSON_OBJECT:
                out.line("Object raw = parser.readRaw();");
                out.line("return raw == null ? null : new org.sjf4j.JsonObject("
                        + STREAMING_IO + ".castMap(raw));");
                break;
            case JSON_ARRAY:
                out.line("Object raw = parser.readRaw();");
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
                throw new AssertionError("No read helper for " + value.kind());
        }

        out.endBlock();
        if (fastjson2Pojo != null && value.creator() == null
                && (value.kind() == BindingValue.Kind.POJO || value.kind() == BindingValue.Kind.JOJO)
                && !value.properties().isEmpty()) {
            out.blank();
            fastjson2Pojo.emitFallback(out, value, this);
        }
    }

    /** Compile CURRENT/key OneOf without depending on discriminator position. */
    void emitOneOfReadHelper(JavaWriter out, BindingValue value) {
        String info = value.helperName() + "_info";
        out.beginBlock("private " + value.type() + " " + value.helperName()
                + "(" + backend.parserType() + " parser) throws java.io.IOException");
        out.beginBlock("if (!parser.nextIfObjectStart())");
        out.beginBlock("if (parser.nextIfNull())");
        out.line("return null;");
        out.endBlock();
        // Mirror OneOfIO for non-object inputs when FAILBACK_NULL is configured.
        out.beginBlock("if (" + info + ".fallbackNull)");
        out.line("parser.skipNode();");
        out.line("return null;");
        out.endBlock();
        out.line("parser.readObjectStart();");
        out.endBlock();

        // The usual discriminator-first layout allocates nothing for buffering.
        // Earlier names retain their actual JSON order for duplicate detection.
        out.line("String[] prefixNames = null;");
        out.line("Object[] prefixValues = null;");
        out.line("int prefixSize = 0;");
        out.line("int nameIndex;");
        out.beginBlock("while ((nameIndex = parser.nextNameMatch("
                + value.helperName() + "_discriminator, 0)) != " + NAME_MATCHER + ".OBJECT_END)");
        out.beginBlock("if (nameIndex == 0)");
        out.line("Object discriminator = parser.readRaw();");
        out.beginBlock("if (discriminator != null)");
        out.beginBlock("switch (String.valueOf(discriminator))");
        for (int i = 0; i < value.oneOfSubtypes().size(); i++) {
            BindingValue.OneOfSubtype subtype = value.oneOfSubtypes().get(i);
            for (String when : subtype.when) {
                out.line("case " + JavaWriter.stringLiteral(when) + ":");
            }
            out.indent();
            out.line("return " + value.helperName() + "_subtype" + i
                    + "_content(parser, prefixNames, prefixValues, prefixSize, discriminator);");
            out.dedent();
        }
        out.endBlock(); // switch
        out.endBlock(); // if non-null
        out.beginBlock("if (" + info + ".fallbackNull)");
        out.beginBlock("while (!parser.nextIfObjectEnd())");
        out.line("parser.nextName();");
        out.line("parser.skipNode();");
        out.endBlock();
        out.line("return null;");
        out.endBlock();
        out.beginBlock("if (discriminator == null)");
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("not found value for discriminator key '" + value.oneOfKey() + "'")
                + ");");
        out.endBlock();
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("oneOf discriminator has no matching mapping: value='")
                + " + discriminator + " + JavaWriter.stringLiteral("'") + ");");
        out.endBlock(); // if discriminator

        out.beginBlock("if (prefixNames == null)");
        out.line("prefixNames = new String[4];");
        out.line("prefixValues = new Object[4];");
        out.endBlock(" else if (prefixSize == prefixNames.length) {");
        out.indent();
        out.line("prefixNames = java.util.Arrays.copyOf(prefixNames, prefixSize << 1);");
        out.line("prefixValues = java.util.Arrays.copyOf(prefixValues, prefixSize << 1);");
        out.endBlock();
        out.line("prefixNames[prefixSize] = parser.currentName();");
        out.line("prefixValues[prefixSize++] = parser.readRaw();");
        out.endBlock(); // while

        out.beginBlock("if (" + info + ".fallbackNull)");
        out.line("return null;");
        out.endBlock();
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("not found value for discriminator key '" + value.oneOfKey() + "'")
                + ");");
        out.endBlock();
    }

    /** Continue parsing after the discriminator; replay only the raw prefix. */
    void emitOneOfSubtypeContent(JavaWriter out, BindingValue owner,
                                 BindingValue subtype, int index) {
        out.beginBlock("private " + subtype.type() + " " + owner.helperName()
                + "_subtype" + index + "_content(" + backend.parserType()
                + " parser, String[] prefixNames, Object[] prefixValues, int prefixSize,"
                + " Object discriminator) throws java.io.IOException");
        emitPojo(out, subtype, true, owner.oneOfKey());
        out.endBlock();
    }

    /** Reuse normal property assignment and Creator duplicate checks for buffered fields. */
    private void emitOneOfPrefixReplay(JavaWriter out, BindingValue subtype) {
        out.beginBlock("for (int prefixIndex = 0; prefixIndex < prefixSize; prefixIndex++)");
        out.line("Object prefixRaw = prefixValues[prefixIndex];");
        out.beginBlock("switch (prefixNames[prefixIndex])");
        for (BindingProperty property : subtype.properties()) {
            out.line("case " + JavaWriter.stringLiteral(property.name()) + ":");
            for (String alias : property.aliases()) {
                out.line("case " + JavaWriter.stringLiteral(alias) + ":");
            }
            out.indent();
            emitPropertyReadExpression(out, property, "value",
                    readOneOfPrefixValue(property.value(), "prefixRaw"));
            out.line("break;");
            out.dedent();
        }
        out.line("default:");
        out.indent();
        out.line("break;"); // unknown fields do not belong to compiled POJOs
        out.dedent();
        out.endBlock(); // switch
        out.endBlock(); // for
    }

    /** Raw scalar conversion for the compile-time-safe OneOf subtype subset. */
    private String readOneOfPrefixValue(BindingValue value, String raw) {
        String numeric = "org.sjf4j.util.Numbers.";
        String decoded;
        switch (value.kind()) {
            case STRING: return "(String) " + raw;
            case BOOLEAN: return "(Boolean) " + raw;
            case NUMBER: return "(Number) " + raw;
            case BYTE: decoded = numeric + "toByte((Number) " + raw + ")"; break;
            case SHORT: decoded = numeric + "toShort((Number) " + raw + ")"; break;
            case INT: decoded = numeric + "toInt((Number) " + raw + ")"; break;
            case LONG: decoded = numeric + "toLong((Number) " + raw + ")"; break;
            case FLOAT: decoded = numeric + "toFloat((Number) " + raw + ")"; break;
            case DOUBLE: decoded = numeric + "toDouble((Number) " + raw + ")"; break;
            case BIG_INTEGER: decoded = numeric + "toBigInteger((Number) " + raw + ")"; break;
            case BIG_DECIMAL: decoded = numeric + "toBigDecimal((Number) " + raw + ")"; break;
            case VALUE_CODEC:
                // readRaw() may return Integer when the selected codec expects Long.
                String codec = value.helperName();
                return "(" + value.type() + ") " + codec + ".rawToValue("
                        + raw + " instanceof Number && Number.class.isAssignableFrom(" + codec + ".rawClazz)"
                        + " ? " + numeric + "to((Number) " + raw + ", " + codec + ".rawClazz)"
                        + " : " + raw + ")";
            default:
                throw new AssertionError("Unsupported compiled OneOf prefix type " + value.kind());
        }
        return value.primitive() ? decoded : "(" + raw + " == null ? null : " + decoded + ")";
    }

    private void emitPojo(JavaWriter out, BindingValue value) {
        emitPojo(out, value, false, null);
    }

    private void emitPojo(JavaWriter out, BindingValue value, boolean insideObject,
                          String consumedDiscriminator) {
        if (value.creator() != null) {
            emitCreator(out, value, insideObject, consumedDiscriminator);
            return;
        }

        if (!insideObject) emitObjectStart(out);

        out.line(value.type() + " value = new " + value.type() + "();");
        if (consumedDiscriminator != null) emitOneOfPrefixReplay(out, value);

        if (fastjson2Pojo != null && !value.properties().isEmpty()) {
            fastjson2Pojo.emitProperties(out, value, this, false);
        } else if (backend.usesNameMatcher() && !value.properties().isEmpty()) {
            out.line("int nameIndex;");
            if (backend.usesExpectedNameMatch()) {
                out.line("int expectedNameIndex = 0;");
            }

            out.beginBlock("while ((nameIndex = parser.nextNameMatch(" + matcherFieldName(value)
                    + (backend.usesExpectedNameMatch() ? ", expectedNameIndex" : "")
                    + ")) != " + NAME_MATCHER + ".OBJECT_END)");

            emitMatchedPojoProperty(out, value, true, consumedDiscriminator);
        } else {
            out.line("String name;");
            out.beginBlock("while ((name = parser.nextName()) != null)");

            emitStringPojoProperty(out, value, consumedDiscriminator);
        }

        if (fastjson2Pojo == null || value.properties().isEmpty()) {
            out.endBlock();
        }
        out.line("return value;");
    }

    /** Compiled creator binding: typed arguments and deferred writable fields. */
    private void emitCreator(JavaWriter out, BindingValue value, boolean insideObject,
                             String consumedDiscriminator) {
        if (!insideObject) emitObjectStart(out);

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
        if (consumedDiscriminator != null) {
            // The dispatcher consumed this String property. Initialize its
            // creator argument and duplicate-detection bit directly.
            for (BindingProperty property : value.properties()) {
                int index = property.creatorIndex();
                if (index < 0) continue;
                boolean matches = consumedDiscriminator.equals(property.name());
                if (!matches) {
                    for (String alias : property.aliases()) {
                        if (consumedDiscriminator.equals(alias)) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (!matches) continue;
                if (property.value().kind() != BindingValue.Kind.STRING) {
                    throw new AssertionError("Compiled discriminator creator argument must be String");
                }
                out.line("arg" + index + " = String.valueOf(discriminator);");
                out.line(argCount <= 64 ? "seen |= (1L << " + index + ");"
                        : "seen[" + index + "] = true;");
            }
            emitOneOfPrefixReplay(out, value);
        }

        int size = value.properties().size();
        if (fastjson2Pojo != null && size != 0) {
            fastjson2Pojo.emitProperties(out, value, this, true);
        } else if (backend.usesNameMatcher() && size != 0) {
            out.line("int nameIndex;");
            if (backend.usesExpectedNameMatch()) out.line("int expectedNameIndex = 0;");
            out.beginBlock("while ((nameIndex = parser.nextNameMatch(" + matcherFieldName(value)
                    + (backend.usesExpectedNameMatch() ? ", expectedNameIndex" : "")
                    + ")) != " + NAME_MATCHER + ".OBJECT_END)");
            emitMatchedPojoProperty(out, value, true, consumedDiscriminator);
        } else {
            out.line("String name;");
            out.beginBlock("while ((name = parser.nextName()) != null)");
            emitStringPojoProperty(out, value, consumedDiscriminator);
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

    void emitMatchedPojoProperty(JavaWriter out, BindingValue value, boolean updateExpected) {
        emitMatchedPojoProperty(out, value, updateExpected, null);
    }

    private void emitMatchedPojoProperty(JavaWriter out, BindingValue value,
                                         boolean updateExpected, String consumedDiscriminator) {
        out.beginBlock("switch (nameIndex)");

        int index = 0;
        for (BindingProperty property : value.properties()) {
            int propertyIndex = index++;
            out.line("case " + propertyIndex + ":");
            out.indent();
            if (updateExpected && backend.usesExpectedNameMatch()) {
                out.line("expectedNameIndex = " + (propertyIndex + 1) + ";");
            }
            emitPropertyRead(out, property, "value", "parser");
            out.line("break;");
            out.dedent();
        }

        out.line("default:");
        out.indent();
        emitDuplicateOneOfCheck(out, consumedDiscriminator, "parser.currentName()");
        emitUnknownObjectProperty(out, value, "parser.currentName()");
        out.line("break;");
        out.dedent();

        out.endBlock();
    }

    private void emitStringPojoProperty(JavaWriter out, BindingValue value,
                                        String consumedDiscriminator) {
        out.beginBlock("switch (name)");

        for (BindingProperty property : value.properties()) {
            out.line("case " + JavaWriter.stringLiteral(property.name()) + ":");

            out.indent();

            emitPropertyRead(out, property, "value", "parser");

            out.line("break;");
            out.dedent();

            for (String alias : property.aliases()) {
                out.line("case " + JavaWriter.stringLiteral(alias) + ":");
                out.indent();
                emitPropertyRead(out, property, "value", "parser");
                out.line("break;");
                out.dedent();
            }
        }

        out.line("default:");
        out.indent();
        emitDuplicateOneOfCheck(out, consumedDiscriminator, "name");
        emitUnknownObjectProperty(out, value, "name");
        out.line("break;");
        out.dedent();

        out.endBlock();
    }

    private void emitDuplicateOneOfCheck(JavaWriter out, String discriminator, String name) {
        if (discriminator == null) return;
        out.beginBlock("if (" + JavaWriter.stringLiteral(discriminator) + ".equals(" + name + "))");
        out.line("throw new org.sjf4j.exception.BindingException("
                + JavaWriter.stringLiteral("duplicate oneOf discriminator key '" + discriminator + "'")
                + ");");
        out.endBlock();
    }

    /** Only JOJO unknown members are dynamic; read-only declared names remain ignored. */
    void emitUnknownObjectProperty(JavaWriter out, BindingValue value, String nameExpression) {
        if (value.kind() != BindingValue.Kind.JOJO || !value.readDynamic()) {
            out.line("parser.skipNode();");
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
            out.line("parser.skipNode();");
            out.endBlock(" else {");
            out.indent();
        }
        out.line("java.util.LinkedHashMap<String, Object> dynamic = org.sjf4j.InternalAccess.dynamicMembers(value);");
        out.beginBlock("if (dynamic == null)");
        out.line("dynamic = new java.util.LinkedHashMap<>();");
        out.line("org.sjf4j.InternalAccess.dynamicMembers(value, dynamic);");
        out.endBlock();
        out.line("dynamic.put(unknownName, parser.readRaw());");
        if (!value.ignoredNames().isEmpty()) out.endBlock();
    }

    String matcherFieldName(BindingValue value) {
        return value.helperName() + "_names";
    }

    void emitPropertyRead(JavaWriter out, BindingProperty property, String owner, String parser) {
        emitPropertyReadExpression(out, property, owner, readExpression(property.value(), parser));
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

        out.beginBlock("while (!parser.nextIfArrayEnd())");
        out.beginBlock("if (size == value.length)");
        out.line("value = java.util.Arrays.copyOf(value, size << 1);");
        out.endBlock();
        out.line("value[size++] = " + readExpression(value.elementValue(), "parser") + ";");
        out.endBlock();

        out.line("return size == value.length ? value : java.util.Arrays.copyOf(value, size);");
    }

    private void emitList(JavaWriter out, BindingValue value) {
        // Raw List<Object> is already handled by each backend's raw parser.
        if (context.typeUtils.erasure(value.type()).toString().equals("java.util.List")
                && context.types.isObject(value.elementValue().type())) {
            out.line("Object raw = parser.readRaw();");
            out.line("return raw == null ? null : " + STREAMING_IO + ".castList(raw);");
            return;
        }

        emitArrayStart(out);

        out.line(value.type() + " value = new java.util.ArrayList<>();");
        out.beginBlock("while (!parser.nextIfArrayEnd())");
        out.line("value.add(" + readExpression(value.elementValue(), "parser") + ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitSet(JavaWriter out, BindingValue value) {
        emitArrayStart(out);

        out.line(value.type() + " value = new java.util.LinkedHashSet<>();");
        out.beginBlock("while (!parser.nextIfArrayEnd())");
        out.line("value.add(" + readExpression(value.elementValue(), "parser") + ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitMap(JavaWriter out, BindingValue value) {
        // Raw Map<String, Object> uses the same backend-independent strategy
        // as List<Object>; typed containers keep their compiled element plan.
        if (context.typeUtils.erasure(value.type()).toString().equals("java.util.Map")
                && context.types.isObject(value.mapValue().type())) {
            out.line("Object raw = parser.readRaw();");
            out.line("return raw == null ? null : " + STREAMING_IO + ".castMap(raw);");
            return;
        }

        emitObjectStart(out);

        out.line(value.type() + " value = new java.util.LinkedHashMap<>();");

        out.line("String name;");
        out.beginBlock("while ((name = parser.nextName()) != null)");
        out.line("value.put(name, " + readExpression(value.mapValue(), "parser") + ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitObjectStart(JavaWriter out) {
        out.beginBlock("if (!parser.nextIfObjectStart())");
        out.beginBlock("if (parser.nextIfNull())");
        out.line("return null;");
        out.endBlock();
        out.line("parser.readObjectStart();");
        out.endBlock();
    }

    private void emitArrayStart(JavaWriter out) {
        out.beginBlock("if (!parser.nextIfArrayStart())");
        out.beginBlock("if (parser.nextIfNull())");
        out.line("return null;");
        out.endBlock();
        out.line("parser.readArrayStart();");
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

    private String readExpression(BindingValue value, String parser) {
        if (value.helperRequired()) {
            return value.helperName() + "(" + parser + ")";
        }

        switch (value.kind()) {
            case STRING:
                return parser + ".readString()";

            case CHARACTER:
                return value.primitive()
                        ? parser + ".readCharValue()"
                        : parser + ".readChar()";

            case BOOLEAN:
                return value.primitive()
                        ? parser + ".readBooleanValue()"
                        : parser + ".readBoolean()";

            case BYTE:
                return value.primitive()
                        ? parser + ".readByteValue()"
                        : parser + ".readByte()";

            case SHORT:
                return value.primitive()
                        ? parser + ".readShortValue()"
                        : parser + ".readShort()";

            case INT:
                return value.primitive()
                        ? parser + ".readIntValue()"
                        : parser + ".readInt()";

            case LONG:
                return value.primitive()
                        ? parser + ".readLongValue()"
                        : parser + ".readLong()";

            case FLOAT:
                return value.primitive()
                        ? parser + ".readFloatValue()"
                        : parser + ".readFloat()";

            case DOUBLE:
                return value.primitive()
                        ? parser + ".readDoubleValue()"
                        : parser + ".readDouble()";

            case NUMBER:
                return parser + ".readNumber()";

            case BIG_INTEGER:
                return parser + ".readBigInteger()";

            case BIG_DECIMAL:
                return parser + ".readBigDecimal()";

            case VALUE_CODEC:
                String rawReader = codecRawReader(value);
                String runtimeRead = STREAMING_IO + ".readValueCodec(" + parser + ", "
                        + value.helperName() + ", this.context)";
                String scalarRead;
                if (rawReader == null) {
                    scalarRead = runtimeRead;
                } else {
                    String rawClass = value.codecRawType().toString();
                    scalarRead = "(" + value.helperName() + ".rawClazz == "
                            + rawClass + ".class ? " + value.helperName()
                            + ".rawToValue(" + parser + "." + rawReader + "()) : "
                            + runtimeRead + ")";
                }
                // The selected codec owns its streaming path. Preserve the
                // existing typed-raw fast path for ordinary ValueCodecs.
                scalarRead = "(" + value.helperName() + ".streamParser != null ? "
                        + value.helperName() + ".streamParser.read(" + parser + ") : "
                        + scalarRead + ")";
                boolean autoRead = value.codecName() == null && value.codecPattern() == null;
                if (autoRead) {
                    String set = value.helperName() + "Set";
                    String selected = STREAMING_IO + ".readValueSet(" + parser + ", "
                            + value.type() + ".class, " + value.type() + ".class, "
                            + set + ", this.context)";
                    return "(" + value.type() + ") (" + set + ".multiples ? "
                            + selected + " : " + scalarRead + ")";
                }
                return "(" + value.type() + ") " + scalarRead;

            case ENUM:
                // Keep string, ordinal and null semantics aligned with StreamingIO.
                return "(" + value.type() + ") " + STREAMING_IO + ".readEnum(" + parser + ", "
                        + value.type() + ".class, this.context)";

            case ONE_OF:
                if (value.oneOfKey() != null) {
                    return value.helperName() + "(" + parser + ")";
                }
                return "(" + value.type() + ") org.sjf4j.binding.OneOfIO"
                        + ".readOneOf(" + parser + ", " + value.helperName() + "_info, this.context)";

            case RUNTIME:
                if (context.types.isObject(value.type())) {
                    return "(" + value.type() + ") " + parser + ".readRaw()";
                }
                return "(" + value.type() + ") " + STREAMING_IO + ".readNode(" + parser + ", "
                        + context.typeUtils.erasure(value.type()) + ".class, this.context)";

            default:
                throw new AssertionError("Unsupported read kind " + value.kind());
        }
    }

    private String assignment(PropertyAccess access, String owner, String expression) {
        if (access.isMethod()) {
            return owner + '.' + access.memberName() + '(' + expression + ");";
        }

        return owner + '.' + access.memberName() + " = " + expression + ";";
    }

    private NameAllocator methodNames(BindingPlan plan) {
        NameAllocator names = new NameAllocator();
        for (VariableElement parameter : plan.method().declaration().getParameters()) {
            names.reserve(parameter.getSimpleName().toString());
        }

        return names;
    }
}
