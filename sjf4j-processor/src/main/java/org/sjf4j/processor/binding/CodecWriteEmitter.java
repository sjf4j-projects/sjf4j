package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;

/** Generates a raw-type-specialized Codec write, retaining runtime format safety. */
final class CodecWriteEmitter {

    private CodecWriteEmitter() {}

    static void emit(JavaWriter out, BindingValue value, String expression,
                     String writer, String nativeWriter, Backend backend) {
        out.beginBlock("");
        out.line(value.type() + " codecValue = " + expression + ";");
        out.beginBlock("if (codecValue == null)");
        out.line((nativeWriter == null ? writer : nativeWriter) + ".writeNull();");
        out.endBlock(" else {");
        out.indent();

        String info = value.helperName();

        // The identity guard is intentional: configured formats, patterns and
        // runtime codec registrations must retain their selected Codec semantics.
        // Only the canonical built-in instance can take this direct route.
        BuiltInDirect builtIn = builtInDirect(value.type().toString());
        if (builtIn != null) {
            out.beginBlock("if ((Object) " + info + ".codec == org.sjf4j.value.ValueCodec."
                    + builtIn.constant + ")");
            String valueExpr = builtIn.rawString ? "codecValue.toString()" : "codecValue";
            String target = builtIn.rawString && nativeWriter != null ? nativeWriter : writer;
            out.line(target + "." + builtIn.method + "(" + valueExpr + ");");
            out.endBlock(" else {");
            out.indent();
        }

        out.beginBlock("if (" + info + ".streamWriter != null)");
        out.line(info + ".streamWriter.write(" + writer + ", codecValue);");
        out.endBlock(" else {");
        out.indent();
        String method = value.codecRawType() == null ? null
                : nativeWrite(value.codecRawType().toString(), backend, nativeWriter != null);
        if (method == null) {
            out.line(writer + ".writeRaw(" + info
                    + ".valueToRaw(codecValue), this.context);");
        } else {
            out.line("Object rawValue = " + info + ".valueToRaw(codecValue);");
            out.beginBlock("if (" + info + ".rawClazz == "
                    + value.codecRawType() + ".class)");
            out.beginBlock("if (rawValue == null)");
            out.line((nativeWriter == null ? writer : nativeWriter) + ".writeNull();");
            out.endBlock(" else {");
            out.indent();
            out.line((nativeWriter == null ? writer : nativeWriter) + "." + method
                    + "(" + rawArgument(value.codecRawType().toString(), backend,
                            nativeWriter != null) + ");");
            out.endBlock();
            out.endBlock(" else {");
            out.indent();
            // A different RuntimeContext format or later registration may use
            // a different raw type. Do not force the compiler's selected shape.
            out.line(writer + ".writeRaw(rawValue, this.context);");
            out.endBlock();
        }
        out.endBlock();
        if (builtIn != null) out.endBlock();
        out.endBlock();
        out.endBlock();
    }

    /**
     * Canonical streaming built-ins; raw-only, patterned, and custom Codec
     * implementations continue through the normal ValueInfo path.
     */
    private static BuiltInDirect builtInDirect(String valueType) {
        switch (valueType) {
            case "java.util.UUID": return new BuiltInDirect("UUID_CODEC", "writeUuid");
            // URI has no dedicated StreamingWriter method, but its canonical
            // Codec is exactly URI::toString. Write that raw String directly.
            case "java.net.URI": return new BuiltInDirect("URI_CODEC", "writeString", true);
            case "java.time.Instant": return new BuiltInDirect("INSTANT_STR", "writeInstant");
            case "java.time.LocalDate": return new BuiltInDirect("LOCAL_DATE", "writeLocalDate");
            case "java.time.LocalTime": return new BuiltInDirect("LOCAL_TIME", "writeLocalTime");
            case "java.time.LocalDateTime": return new BuiltInDirect("LOCAL_DATE_TIME", "writeLocalDateTime");
            case "java.time.OffsetDateTime": return new BuiltInDirect("OFFSET_DATE_TIME", "writeOffsetDateTime");
            case "java.time.OffsetTime": return new BuiltInDirect("OFFSET_TIME", "writeOffsetTime");
            case "java.time.ZonedDateTime": return new BuiltInDirect("ZONED_DATE_TIME", "writeZonedDateTime");
            default: return null;
        }
    }

    private static final class BuiltInDirect {
        final String constant;
        final String method;
        final boolean rawString;

        BuiltInDirect(String constant, String method) {
            this(constant, method, false);
        }

        BuiltInDirect(String constant, String method, boolean rawString) {
            this.constant = constant;
            this.method = method;
            this.rawString = rawString;
        }
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
