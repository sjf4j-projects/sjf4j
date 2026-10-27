package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;

/** Generates a selected-Codec streaming path with raw-type-specialized fallback. */
final class CodecWriteEmitter {

    private CodecWriteEmitter() {}

    static void emit(JavaWriter out, BindingValue value, String expression,
                     String generator, String nativeWriter, Backend backend) {
        out.beginBlock("");
        out.line(value.type() + " codecValue = " + expression + ";");
        out.beginBlock("if (codecValue == null)");
        out.line((nativeWriter == null ? generator : nativeWriter) + ".writeNull();");
        out.endBlock(" else {");
        out.indent();

        String info = value.helperName();

        out.beginBlock("if (" + info + ".streamGenerator != null)");
        out.line(info + ".streamGenerator.write(" + generator + ", codecValue);");
        out.endBlock(" else {");
        out.indent();
        String method = value.codecRawType() == null ? null
                : nativeWrite(value.codecRawType().toString(), backend, nativeWriter != null);
        if (method == null) {
            out.line(generator + ".writeRaw(" + info
                    + ".valueToRaw(codecValue), this.context);");
        } else {
            out.line("Object rawValue = " + info + ".valueToRaw(codecValue);");
            out.beginBlock("if (" + info + ".rawClazz == "
                    + value.codecRawType() + ".class)");
            if (nativeWriter != null && "writeString".equals(method)
                    && (backend == Backend.JACKSON2 || backend == Backend.JACKSON3)) {
                // The Jackson string helper already writes null values.
                out.line("_writeString(" + nativeWriter + ", (String) rawValue);");
            } else {
                out.beginBlock("if (rawValue == null)");
                out.line((nativeWriter == null ? generator : nativeWriter) + ".writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line((nativeWriter == null ? generator : nativeWriter) + "." + method
                        + "(" + rawArgument(value.codecRawType().toString(), backend,
                                nativeWriter != null) + ");");
                out.endBlock();
            }
            out.endBlock(" else {");
            out.indent();
            // A different RuntimeContext format or later registration may use
            // a different raw type. Do not force the compiler's selected shape.
            out.line(generator + ".writeRaw(rawValue, this.context);");
            out.endBlock();
        }
        out.endBlock();
        out.endBlock();
        out.endBlock();
    }

    private static String nativeWrite(String raw, Backend backend, boolean nativeWriter) {
        if (!nativeWriter) {
            switch (raw) {
                case "java.lang.String": return "writeStringValue";
                case "java.lang.Boolean": return "writeBooleanValue";
                case "java.lang.Byte": return "writeByteValue";
                case "java.lang.Short": return "writeShortValue";
                case "java.lang.Integer": return "writeIntValue";
                case "java.lang.Long": return "writeLongValue";
                case "java.lang.Float": return "writeFloatValue";
                case "java.lang.Double": return "writeDoubleValue";
                case "java.math.BigInteger": return "writeBigIntegerValue";
                case "java.math.BigDecimal": return "writeBigDecimalValue";
                case "java.lang.Number": return "writeNumberValue";
                default: return null;
            }
        }
        if (backend == Backend.FASTJSON2) {
            switch (raw) {
                case "java.lang.String": return "writeString";
                case "java.lang.Boolean": return "writeBool";
                case "java.lang.Byte": return "writeInt8";
                case "java.lang.Short": return "writeInt16";
                case "java.lang.Integer": return "writeInt32";
                case "java.lang.Long": return "writeInt64";
                case "java.lang.Float": return "writeFloat";
                case "java.lang.Double": return "writeDouble";
                case "java.math.BigInteger": return "writeBigInt";
                case "java.math.BigDecimal": return "writeDecimal";
                default: return null;
            }
        }
        if (backend == Backend.JACKSON2 || backend == Backend.JACKSON3) {
            switch (raw) {
                case "java.lang.String": return "writeString";
                case "java.lang.Boolean": return "writeBoolean";
                case "java.lang.Byte":
                case "java.lang.Short":
                case "java.lang.Integer":
                case "java.lang.Long":
                case "java.lang.Float":
                case "java.lang.Double":
                case "java.math.BigInteger":
                case "java.math.BigDecimal": return "writeNumber";
                default: return null;
            }
        }
        return null;
    }

    private static String rawArgument(String raw, Backend backend, boolean nativeWriter) {
        String value = "((" + raw + ") rawValue)";
        if (raw.equals("java.lang.Byte") || raw.equals("java.lang.Short")) {
            if (nativeWriter && (backend == Backend.JACKSON2 || backend == Backend.JACKSON3)) {
                return value + ".intValue()";
            }
            return value + "." + (raw.equals("java.lang.Byte")
                    ? "byteValue()" : "shortValue()");
        }
        if (raw.equals("java.lang.Integer")) return value + ".intValue()";
        if (raw.equals("java.lang.Long")) return value + ".longValue()";
        if (raw.equals("java.lang.Float")) return value + ".floatValue()";
        if (raw.equals("java.lang.Double")) return value + ".doubleValue()";
        if (raw.equals("java.lang.Boolean")) return value + ".booleanValue()";
        return value;
    }
}
