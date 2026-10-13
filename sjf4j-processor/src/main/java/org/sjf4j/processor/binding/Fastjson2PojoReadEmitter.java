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

    void emitProperties(JavaWriter out, BindingValue value, ReadEmitter reads) {
        out.line("com.alibaba.fastjson2.JSONReader raw = reader.rawReader();");
        out.beginBlock("while (!reader.nextIfObjectEnd())");

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
            out.beginBlock("if (java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN)");
            out.beginBlock("switch (raw.getRawInt())");
            for (Map.Entry<Integer, List<BindingProperty>> entry : rawNames.entrySet()) {
                out.line("case 0x" + Integer.toHexString(entry.getKey()) + ":");
                out.indent();
                for (BindingProperty property : entry.getValue()) {
                    out.beginBlock("if (" + rawMatchCall(property.name()) + ")");
                    reads.emitPropertyRead(out, property, "value", "reader");
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

        out.line(fallbackMethod(value) + "(reader, value);");
        out.endBlock();
    }

    void emitFallback(JavaWriter out, BindingValue value, ReadEmitter reads) {
        out.beginBlock("private static void " + fallbackMethod(value)
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
        out.line("reader.skipNode();");
        out.line("break;");
        out.dedent();
        out.endBlock();
        out.endBlock();
    }

    private static String fallbackMethod(BindingValue value) {
        return value.helperName() + "_fallback";
    }

    private static boolean rawEligible(String name) {
        int length = name.length();
        if (length < 2 || length > 11) return false;
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

    private static String rawMatchCall(String name) {
        int length = name.length();
        String literal = "\"" + name + "\":";
        long suffix = pack(literal, 4, Math.min(8, literal.length() - 4));
        String call = "raw.nextIfName4Match" + length + "(";
        switch (length) {
            case 2:
            case 3:
                return call + ")";
            case 4:
                return call + "(byte) 0x" + Long.toHexString(suffix & 0xffL) + ")";
            case 5:
            case 6:
            case 7:
                return call + "0x" + Long.toHexString(suffix & 0xffffffffL) + ")";
            case 8:
                return call + "0x" + Long.toHexString(suffix & 0xffffffffL)
                        + ", (byte) 0x" + Long.toHexString(suffix >>> 32) + ")";
            case 9:
            case 10:
            case 11:
                return call + "0x" + Long.toHexString(suffix) + "L)";
            default:
                throw new AssertionError("Unsupported raw-name length: " + length);
        }
    }
}
