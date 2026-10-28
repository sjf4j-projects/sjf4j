package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Fastjson2 compiled write generation using a shared streaming/native generator. */
final class Fastjson2WriteEmitter {

    private enum FastjsonMode {
        UTF8("Utf8"), UTF16("Utf16");

        final String suffix;

        FastjsonMode(String suffix) {
            this.suffix = suffix;
        }
    }

    private final Set<FastjsonMode> fastjsonModes = EnumSet.noneOf(FastjsonMode.class);
    private final WriteEmitter writes;

    Fastjson2WriteEmitter(WriteEmitter writes) {
        this.writes = writes;
    }

    /** Backend-specific UTF-8/UTF-16 quoted JSON literals, initialized once per Enum. */
    void emitEnumFields(JavaWriter out, Map<String, String> fields) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String type = entry.getKey();
            String field = entry.getValue();
            for (boolean utf8 : new boolean[]{true, false}) {
                String suffix = utf8 ? "Utf8" : "Utf16";
                String elementType = utf8 ? "byte" : "char";
                String method = field + suffix + "Init";
                out.line("private static final " + elementType + "[][] " + field + suffix
                        + " = " + method + "();");
                out.beginBlock("private static " + elementType + "[][] " + method + "()");
                out.line(type + "[] constants = " + type + ".values();");
                out.line(elementType + "[][] result = new " + elementType + "[constants.length][];");
                out.beginBlock("for (" + type + " constant : constants)");
                out.line("String quoted = com.alibaba.fastjson2.JSON.toJSONString(constant.name());");
                out.line("result[constant.ordinal()] = " + (utf8
                        ? "quoted.getBytes(java.nio.charset.StandardCharsets.UTF_8);"
                        : "quoted.toCharArray();"));
                out.endBlock();
                out.line("return result;");
                out.endBlock();
            }
        }
    }

    void registerWriteOutput(BindingPlan.WriteOutput output) {
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

    private static boolean fixedRawName(String name) {
        int length = name.length();
        if (length < 2 || length > 16) return false;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c > 0x7E || c == '"' || c == '\\') return false;
        }
        return true;
    }

    /** Emits Fastjson2 code through one buffer shared by native and codec writes. */
    void emitMethod(JavaWriter out, BindingValue value, String source, String target,
                    BindingPlan.WriteOutput output, NameAllocator names) {
        boolean utf8 = output == BindingPlan.WriteOutput.BYTES
                || output == BindingPlan.WriteOutput.OUTPUT_STREAM;
        FastjsonMode mode = utf8 ? FastjsonMode.UTF8 : FastjsonMode.UTF16;
        String generator = names.newName("generator");
        String raw = names.newName("raw");
        // Never attach the caller-owned destination before serialization succeeds.
        // close() flushes attached writers even if a compiled helper throws.
        String create = utf8 ? "createGeneratorUtf8()" : "createGenerator()";

        out.beginBlock("try (org.sjf4j.backend.fastjson2.binding.Fastjson2Generator "
                + generator + " = this.binder." + create + ")");
        out.line("com.alibaba.fastjson2.JSONWriter " + raw + " = " + generator + ".nativeWriter();");

        // Raw field-name optimizations require compatible Fastjson2 features.
        out.beginBlock("if (!org.sjf4j.backend.fastjson2.binding.Fastjson2Binder."
                + (utf8 ? "supportsRawUtf8Names(" : "supportsRawUtf16Names(")
                + raw + "))");
        switch (output) {
            case STRING:
                out.line("return this.binder.writeNodeAsString(" + source + ");");
                break;
            case BYTES:
                out.line("return this.binder.writeNodeAsBytes(" + source + ");");
                break;
            case WRITER:
                out.line(target + ".write(this.binder.writeNodeAsString(" + source + "));");
                out.line("return;");
                break;
            case OUTPUT_STREAM:
                out.line(target + ".write(this.binder.writeNodeAsBytes(" + source + "));");
                out.line("return;");
                break;
            default:
                throw new AssertionError(output);
        }
        out.endBlock();

        if (value.helperRequired()) {
            out.line(fastjsonHelperName(value, mode) + "("
                    + generator + ", " + source + ");");
        } else {
            emitNativeValue(out, value, source, mode, raw, generator, names.newName("item"));
        }

        switch (output) {
            case STRING:
                out.line("return " + generator + ".toString();");
                break;
            case BYTES:
                out.line("return " + generator + ".toBytes();");
                break;
            case WRITER:
            case OUTPUT_STREAM:
                // Commit only after the entire JSON value was written successfully.
                out.line(raw + ".flushTo(" + target + ");");
                break;
            default:
                throw new AssertionError(output);
        }
        out.endBlock();
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

    private void emitFastjsonName(JavaWriter out, String name) {
        out.line("w.writeNameRaw(" + name + ");");
    }

    /** Structural code is shared; a mode-specific APT adapter emits native tokens. */
    private final class ModeOperations implements StructuralWriteEmitter.Operations {
        private final FastjsonMode mode;

        ModeOperations(FastjsonMode mode) {
            this.mode = mode;
        }

        @Override
        public void startObject(JavaWriter out) { out.line("w.startObject();"); }

        @Override
        public void endObject(JavaWriter out) { out.line("w.endObject();"); }

        @Override
        public void startArray(JavaWriter out) { out.line("w.startArray();"); }

        @Override
        public void endArray(JavaWriter out) { out.line("w.endArray();"); }

        @Override
        public void writeNull(JavaWriter out) { out.line("w.writeNull();"); }

        // writeNameRaw/writeNameXRaw and writeName(String) insert object-member
        // separators themselves. Adding writeComma() here would corrupt JSON.
        @Override
        public void writePropertyName(JavaWriter out, BindingValue owner, int index) {
            BindingProperty property = owner.properties().get(index);
            String field = WriteEmitter.compiledNameField(owner, index);
            if (fixedRawName(property.name())) {
                int length = property.name().length();
                String args = field + ".rawName0";
                if (length == 9) args += ", (int) " + field + ".rawName1";
                else if (length > 9) args += ", " + field + ".rawName1";
                out.line("w.writeName" + length + "Raw(" + args + ");");
            } else {
                emitFastjsonName(out, fastjsonNameExpr(field, mode));
            }
        }

        @Override
        public void writeDynamicName(JavaWriter out, String expression) {
            out.line("w.writeName(" + expression + ");");
            out.line("w.writeColon();");
        }

        @Override
        public void writeMapName(JavaWriter out, String expression) {
            writeDynamicName(out, expression);
        }

        @Override
        public void writeValue(JavaWriter out, BindingValue value, String expression) {
            emitNativeValue(out, value, expression, mode);
        }

        @Override
        public void writeNonNullValue(JavaWriter out, BindingValue value, String expression) {
            switch (value.kind()) {
                case STRING:
                    out.line("w.writeString(" + expression + ");");
                    return;
                case CHARACTER:
                    out.line("w.writeString(" + expression + ".toString());");
                    return;
                case ENUM:
                    String field = writes.enumField(value) + mode.suffix;
                    if (mode == FastjsonMode.UTF8) {
                        out.line("w.writeRaw(" + field + "[" + expression + ".ordinal()]);");
                    } else {
                        out.line("char[] encoded = " + field + "[" + expression + ".ordinal()];");
                        out.line("w.writeRaw(encoded, 0, encoded.length);");
                    }
                    return;
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                case BIG_INTEGER:
                case BIG_DECIMAL:
                    String arg = fastjsonUnboxArgument(value.type().toString())
                            .replace("value", expression);
                    out.line("w." + fastjsonWriteMethod(value.kind()) + "(" + arg + ");");
                    return;
                default:
                    throw new AssertionError(value.kind());
            }
        }

        @Override
        public void writeRuntimeValue(JavaWriter out, String expression) {
            out.line("org.sjf4j.binding.StreamingIO.writeNode(generator, "
                    + expression + ", this.context);");
        }

        @Override
        public boolean explicitArraySeparators() { return true; }

        @Override
        public void beforeArrayElement(JavaWriter out, String ordinal) {
            out.beginBlock("if (" + ordinal + " != 0)");
            out.line("w.writeComma();");
            out.endBlock();
        }
    }

    /** Every structural helper has the same (generator, value) signature. */
    private void emitNativeHelper(JavaWriter out, BindingValue value, FastjsonMode mode) {
        out.beginBlock("private void " + fastjsonHelperName(value, mode)
                + "(org.sjf4j.backend.fastjson2.binding.Fastjson2Generator generator, "
                + value.type() + " value) throws java.io.IOException");
        out.line("com.alibaba.fastjson2.JSONWriter w = generator.nativeWriter();");

        if (value.kind() == BindingValue.Kind.JSON_OBJECT
                || value.kind() == BindingValue.Kind.JSON_ARRAY) {
            out.line("org.sjf4j.binding.StreamingIO.writeNode(generator, value, this.context);");
        } else {
            StructuralWriteEmitter structure = new StructuralWriteEmitter(new ModeOperations(mode));
            if (value.kind() != BindingValue.Kind.ONE_OF) {
                out.beginBlock("if (value == null)");
                out.line("w.writeNull();");
                out.line("return;");
                out.endBlock();
            }

            switch (value.kind()) {
                case POJO:
                case JOJO:
                    structure.emitPojo(out, value);
                    break;
                case LIST:
                    structure.emitList(out, value);
                    break;
                case SET:
                    structure.emitSet(out, value);
                    break;
                case MAP:
                    structure.emitMap(out, value);
                    break;
                case ONE_OF:
                    structure.emitOneOf(out, value);
                    break;
                default:
                    throw new AssertionError(value.kind());
            }
        }
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
        emitNativeValue(out, value, expression, mode, "w", "generator", "item");
    }

    private void emitNativeValue(JavaWriter out, BindingValue value,
            String expression, FastjsonMode mode, String raw, String generator, String item) {
        if (value.helperRequired()) {
            out.line(fastjsonHelperName(value, mode) + "(" + generator + ", " + expression + ");");
            return;
        }

        switch (value.kind()) {
            case VALUE_CODEC:
                CodecWriteEmitter.emit(out, value, expression, generator, raw, Backend.FASTJSON2);
                return;
            case RUNTIME:
            case JSON_OBJECT:
            case JSON_ARRAY:
                out.line("org.sjf4j.binding.StreamingIO.writeNode("
                        + generator + ", " + expression + ", this.context);");
                return;
            case NUMBER:
                out.line(generator + ".writeNumber(" + expression + ");");
                return;
            case STRING:
                out.line("_writeString(" + raw + ", " + expression + ");");
                return;
            case CHARACTER:
                if (value.primitive()) {
                    out.line(raw + ".writeString(Character.toString(" + expression + "));");
                } else {
                    out.beginBlock("");
                    out.line("Character " + item + " = " + expression + ";");
                    out.beginBlock("if (" + item + " == null)");
                    out.line(raw + ".writeNull();");
                    out.endBlock(" else {");
                    out.indent();
                    out.line(raw + ".writeString(" + item + ".toString());");
                    out.endBlock();
                    out.endBlock();
                }
                return;
            case ENUM:
                out.beginBlock("");
                out.line(value.type() + " " + item + " = " + expression + ";");
                out.beginBlock("if (" + item + " == null)");
                out.line(raw + ".writeNull();");
                out.endBlock(" else {");
                out.indent();
                String field = writes.enumField(value) + mode.suffix;
                if (mode == FastjsonMode.UTF8) {
                    out.line(raw + ".writeRaw(" + field + "[" + item + ".ordinal()]);");
                } else {
                    out.line("char[] encoded = " + field + "[" + item + ".ordinal()];");
                    out.line(raw + ".writeRaw(encoded, 0, encoded.length);");
                }
                out.endBlock();
                out.endBlock();
                return;
            default:
                break;
        }

        String method = fastjsonWriteMethod(value.kind());
        if (value.primitive()) {
            out.line(raw + "." + method + "(" + expression + ");");
        } else {
            out.beginBlock("");
            out.line(value.type() + " " + item + " = " + expression + ";");
            out.beginBlock("if (" + item + " == null)");
            out.line(raw + ".writeNull();");
            out.endBlock(" else {");
            out.indent();
            String arg = fastjsonUnboxArgument(value.type().toString()).replace("value", item);
            out.line(raw + "." + method + "(" + arg + ");");
            out.endBlock();
            out.endBlock();
        }
    }

    void emitHelper(JavaWriter out, BindingValue value) {
        for (FastjsonMode mode : fastjsonModes) {
            emitNativeHelper(out, value, mode);
        }
    }
}
