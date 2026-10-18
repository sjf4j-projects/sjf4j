package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits the Fastjson2-only fused field-name dispatch for compiled POJOs.
 *
 * <p>Object construction, member assignment, value conversion and recursive
 * helpers remain owned by {@link ReadEmitter}. Only raw name recognition is
 * specialized. The slower generic matcher path is a separate generated helper
 * so it does not inflate the inlineable field-read loop.</p>
 */
final class Fastjson2PojoReadEmitter {

    void emitProperties(JavaWriter out, BindingValue value, ReadEmitter reads, boolean creator) {
        out.line("com.alibaba.fastjson2.JSONReader raw = reader.rawReader();");
        out.beginBlock("while (!raw.nextIfObjectEnd())");

        Map<Integer, List<BindingProperty>> rawNames =
                new LinkedHashMap<Integer, List<BindingProperty>>();
        for (BindingProperty property : value.properties()) {
            if (!rawEligible(property.name())) continue;
            String literal = "\"" + property.name() + "\":";
            int prefix = (int) pack(literal, 0, 4);
            List<BindingProperty> group = rawNames.get(prefix);
            if (group == null) {
                group = new ArrayList<BindingProperty>();
                rawNames.put(prefix, group);
            }
            group.add(property);
        }

        if (!rawNames.isEmpty()) {
            // Fastjson2's getRawInt is native-endian. On a big-endian JVM,
            // canonical and aliased property names use the normal hash path.
            out.beginBlock("if (FASTJSON2_LITTLE_ENDIAN)");
            out.beginBlock("switch (raw.getRawInt())");
            for (Map.Entry<Integer, List<BindingProperty>> entry : rawNames.entrySet()) {
                out.line("case 0x" + Integer.toHexString(entry.getKey()) + ":");
                out.indent();
                for (BindingProperty property : entry.getValue()) {
                    out.beginBlock("if (" + rawMatchCall(property.name()) + ")");
                    String nativeRead = nativeScalarRead(property.value());
                    if (nativeRead != null) {
                        reads.emitPropertyReadExpression(out, property, "value", nativeRead);
                    } else {
                        reads.emitPropertyRead(out, property, "value", "reader");
                    }
                    out.line("continue;");
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
            out.endBlock();
        }

        if (creator) {
            out.line("int nameIndex = reader.nextNameMatch(" + reads.matcherFieldName(value) + ");");
            reads.emitMatchedPojoProperty(out, value, false);
        } else {
            out.line(fallbackMethod(value) + "(reader, value);");
        }
        out.endBlock();
    }

    void emitFallback(JavaWriter out, BindingValue value, ReadEmitter reads) {
        out.beginBlock("private void " + fallbackMethod(value)
                + "(org.sjf4j.backend.fastjson2.binding.Fastjson2Reader reader, "
                + value.type() + " value) throws java.io.IOException");
        out.line("int nameIndex = reader.nextNameMatch(" + reads.matcherFieldName(value) + ");");
        out.beginBlock("switch (nameIndex)");
        int i = 0;
        for (BindingProperty property : value.properties()) {
            out.line("case " + i++ + ":");
            out.indent();
            reads.emitPropertyRead(out, property, "value", "reader");
            out.line("break;");
            out.dedent();
        }
        out.line("default:");
        out.indent();
        reads.emitUnknownObjectProperty(out, value, "reader.currentName()");
        out.line("break;");
        out.dedent();
        out.endBlock();
        out.endBlock();
    }

    /**
     * Experimental native scalar path. Keep nullable wrappers, enums, custom
     * conversions, containers and fallback on the existing StreamingReader
     * contract. Raw name matching in the enclosing loop guarantees a pending
     * value; the wrapper peek cache is already clear at this point.
     */
    private static String nativeScalarRead(BindingValue value) {
        if (value.kind() == BindingValue.Kind.STRING) {
            return "raw.readString()";
        }
        if (!value.primitive()) {
            return null;
        }
        switch (value.kind()) {
            case BOOLEAN:
                return "raw.readBoolValue()";
            case BYTE:
                return "raw.readInt8Value()";
            case SHORT:
                return "raw.readInt16Value()";
            case INT:
                return "raw.readInt32Value()";
            case LONG:
                return "raw.readInt64Value()";
            case FLOAT:
                return "raw.readFloatValue()";
            case DOUBLE:
                return "raw.readDoubleValue()";
            default:
                return null;
        }
    }

    private static String fallbackMethod(BindingValue value) {
        return value.helperName() + "_fallback";
    }

    private static boolean rawEligible(String name) {
        int length = name.length();
        if (length < 2 || length > 43) return false;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c >= 0x7f || c == '"' || c == '\\') return false;
        }
        return true;
    }

    private static long pack(String literal, int start, int length) {
        long result = 0;
        for (int i = 0; i < length; i++) {
            result |= (long) literal.charAt(start + i) << (8 * i);
        }
        return result;
    }

    /**
     * Emit the native 2..43-character Fastjson2 matcher arguments at compile
     * time. After the four-byte raw prefix, the name has (length - 3)
     * remaining bytes. Each native matcher accepts 8-byte groups followed by
     * either a byte, an int, an int+byte, or an eight-byte group (which may
     * include the closing quote/colon for lengths ending in 6 or 7).
     */
    private static String rawMatchCall(String name) {
        int length = name.length();
        String literal = "\"" + name + "\":";
        StringBuilder call = new StringBuilder("raw.nextIfName4Match")
                .append(length).append('(');

        int remaining = length - 3;
        int offset = 4;
        while (remaining >= 8) {
            appendRawArg(call, literal, offset, 8);
            remaining -= 8;
            offset += 8;
        }

        if (remaining == 1) {
            appendRawArg(call, literal, offset, 1);
        } else if (remaining >= 2 && remaining <= 4) {
            appendRawArg(call, literal, offset, 4);
        } else if (remaining == 5) {
            appendRawArg(call, literal, offset, 4);
            appendRawArg(call, literal, offset + 4, 1);
        } else if (remaining >= 6) {
            appendRawArg(call, literal, offset, 8);
        }

        return call.append(')').toString();
    }

    private static void appendRawArg(
            StringBuilder call, String literal, int offset, int bytes) {
        if (call.charAt(call.length() - 1) != '(') {
            call.append(", ");
        }
        long bits = pack(literal, offset, bytes);
        if (bytes == 1) {
            call.append("(byte) 0x").append(Long.toHexString(bits));
        } else if (bytes == 4) {
            call.append("0x").append(Long.toHexString(bits));
        } else if (bytes == 8) {
            call.append("0x").append(Long.toHexString(bits)).append('L');
        } else {
            throw new AssertionError("Unsupported raw-name argument width: " + bytes);
        }
    }

}
