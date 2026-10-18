package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Fastjson2 compiled write generation using a shared streaming/native writer. */
final class Fastjson2WriteEmitter {

    private enum FastjsonMode {
        UTF8("Utf8"), UTF16("Utf16");

        final String suffix;

        FastjsonMode(String suffix) {
            this.suffix = suffix;
        }
    }

    private final Set<FastjsonMode> fastjsonModes = EnumSet.noneOf(FastjsonMode.class);
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
    void emitMethod(
            JavaWriter out,
            BindingValue value,
            String source,
            String target,
            BindingPlan.WriteOutput output,
            NameAllocator names) {

        boolean utf8 = output == BindingPlan.WriteOutput.BYTES
                || output == BindingPlan.WriteOutput.OUTPUT_STREAM;
        FastjsonMode mode = utf8 ? FastjsonMode.UTF8 : FastjsonMode.UTF16;
        String writer = names.newName("writer");
        String raw = names.newName("raw");
        String create = output == BindingPlan.WriteOutput.STRING
                ? "createWriter()"
                : output == BindingPlan.WriteOutput.BYTES
                        ? "createWriterUtf8()"
                        : "createWriter(" + target + ")";

        out.beginBlock("try (org.sjf4j.backend.fastjson2.binding.Fastjson2Writer "
                + writer + " = this.binder." + create + ")");
        out.line("com.alibaba.fastjson2.JSONWriter " + raw + " = " + writer + ".rawWriter();");

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
            emitNativeValue(out, value, source, mode, raw, writer, names.newName("item"));
        }

        switch (output) {
            case STRING:
                out.line("return " + writer + ".toString();");
                break;
            case BYTES:
                out.line("return " + writer + ".toBytes();");
                break;
            case WRITER:
            case OUTPUT_STREAM:
                out.line(writer + ".flush();");
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

    private void emitNativeHelper(JavaWriter out, BindingValue value, FastjsonMode mode) {
        boolean withCodec = hasCodec(value, new HashSet<BindingValue>());
        out.beginBlock("private " + (withCodec ? "" : "static ") + "void "
                + fastjsonHelperName(value, mode)
                + "(org.sjf4j.backend.fastjson2.binding.Fastjson2Writer writer, "
                + value.type() + " value) throws java.io.IOException");
        out.line("com.alibaba.fastjson2.JSONWriter w = writer.rawWriter();");
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
            String field = WriteEmitter.compiledNameField(value, index++);
            String expr = WriteEmitter.access(property.access(), "value");
            if (fixedRawName(property.name())) {
                int length = property.name().length();
                String args = field + ".rawName0";
                if (length == 9) args += ", (int) " + field + ".rawName1";
                else if (length > 9) args += ", " + field + ".rawName1";
                out.line("w.writeName" + length + "Raw(" + args + ");");
            } else {
                emitFastjsonName(out, fastjsonNameExpr(field, mode));
            }
            emitNativeValue(out, child, expr, mode);
        }
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
        emitNativeValue(out, value, expression, mode, "w", "writer", "item");
    }

    private void emitNativeValue(JavaWriter out, BindingValue value,
            String expression, FastjsonMode mode, String raw, String writer, String item) {
        if (value.helperRequired()) {
            out.line(fastjsonHelperName(value, mode) + "(" + writer + ", " + expression + ");");
            return;
        }

        switch (value.kind()) {
            case VALUE_CODEC:
                out.beginBlock("if (" + expression + " == null)");
                out.line(raw + ".writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line("org.sjf4j.binding.StreamingIO.writeNode(" + writer + ", "
                        + value.helperName() + ".valueToRaw(" + expression + "), this.context);");
                out.endBlock();
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
                out.line(raw + ".writeString(" + item + ".name());");
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
            out.line(raw + ".writeNull();");
            out.endBlock(" else {");
            out.indent();
            String arg = fastjsonUnboxArgument(value.type().toString()).replace("value", item);
            out.line(writer + "." + method + "(" + arg + ");");
            out.endBlock();
            out.endBlock();
        }
    }


    /** Codec raw conversion is local to a field, not a reason to discard native POJO writes. */
    boolean nativeEligible(BindingValue value, Set<BindingValue> visited) {
        if (!visited.add(value)) return true;
        switch (value.kind()) {
            case RUNTIME:
            case NUMBER:
            case JOJO:
            case JSON_OBJECT:
            case JSON_ARRAY:
                return false;
            case POJO:
                for (BindingProperty property : value.properties()) {
                    if (!nativeEligible(property.value(), visited)) return false;
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

    private boolean hasCodec(BindingValue value, Set<BindingValue> visited) {
        if (!visited.add(value)) return false;
        switch (value.kind()) {
            case VALUE_CODEC:
                return true;
            case POJO:
                for (BindingProperty property : value.properties()) {
                    if (hasCodec(property.value(), visited)) return true;
                }
                return false;
            case LIST:
            case SET:
                return hasCodec(value.elementValue(), visited);
            case MAP:
                return hasCodec(value.mapValue(), visited);
            default:
                return false;
        }
    }

    void emitHelper(JavaWriter out, BindingValue value) {
        for (FastjsonMode mode : fastjsonModes) {
            emitNativeHelper(out, value, mode);
        }
    }
}
