package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fastjson2 compiled read generation for POJO field names and Enum values.
 *
 * <p>Object construction, member assignment, value conversion and recursive
 * helpers remain owned by {@link ReadEmitter}. Only raw name recognition is
 * specialized. The slower generic matcher path is a separate generated helper
 * so it does not inflate the inlineable field-read loop.</p>
 */
final class Fastjson2ReadEmitter {

    void emitProperties(JavaWriter out, BindingValue value, ReadEmitter reads, boolean creator) {
        out.line("com.alibaba.fastjson2.JSONReader raw = parser.rawReader();");
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
                        reads.emitPropertyRead(out, property, "value", "parser");
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
            out.line("int nameIndex = parser.nextNameMatch(" + reads.matcherFieldName(value) + ");");
            reads.emitMatchedPojoProperty(out, value, false);
        } else {
            out.line(fallbackMethod(value) + "(parser, value);");
        }
        out.endBlock();
    }

    void emitFallback(JavaWriter out, BindingValue value, ReadEmitter reads) {
        out.beginBlock("private void " + fallbackMethod(value)
                + "(org.sjf4j.backend.fastjson2.binding.Fastjson2Parser parser, "
                + value.type() + " value) throws java.io.IOException");
        out.line("int nameIndex = parser.nextNameMatch(" + reads.matcherFieldName(value) + ");");
        out.beginBlock("switch (nameIndex)");
        int i = 0;
        for (BindingProperty property : value.properties()) {
            out.line("case " + i++ + ":");
            out.indent();
            reads.emitPropertyRead(out, property, "value", "parser");
            out.line("break;");
            out.dedent();
        }
        out.line("default:");
        out.indent();
        reads.emitUnknownObjectProperty(out, value, "parser.currentName()");
        out.line("break;");
        out.dedent();
        out.endBlock();
        out.endBlock();
    }

    /**
     * Experimental native scalar path. Keep nullable wrappers, enums, custom
     * conversions, containers and fallback on the existing StreamingParser
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

    private static void appendRawArg(StringBuilder call, String literal, int offset, int bytes) {
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


    private final Map<String, String> enums = new LinkedHashMap<String, String>();

    void register(BindingValue root) {
        Set<BindingValue> seen = Collections.newSetFromMap(
                new IdentityHashMap<BindingValue, Boolean>());
        register(root, seen);
    }

    private void register(BindingValue value, Set<BindingValue> seen) {
        if (value == null || !seen.add(value)
                || value.direction() != BindingPlan.Direction.READ_FROM) return;
        if (value.kind() == BindingValue.Kind.ENUM) {
            String type = value.type().toString();
            if (!enums.containsKey(type)) {
                enums.put(type, "_enumRead" + enums.size());
            }
        }
        for (BindingProperty property : value.properties()) {
            register(property.value(), seen);
        }
        register(value.elementValue(), seen);
        register(value.mapValue(), seen);
        for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
            register(subtype.compiled, seen);
        }
    }

    boolean hasEnums() {
        return !enums.isEmpty();
    }

    String readExpression(BindingValue value, String parser) {
        String field = enums.get(value.type().toString());
        if (field == null) {
            throw new IllegalStateException("Unregistered compiled Enum: " + value.type());
        }
        return field + "(" + parser + ")";
    }

    void emitFields(JavaWriter out) {
        for (Map.Entry<String, String> entry : enums.entrySet()) {
            String type = entry.getKey();
            String field = entry.getValue();
            out.line("private static final " + type + "[] " + field
                    + "Ordinals = " + type + ".values();");
            out.line("private static final " + type + "[] " + field
                    + "ByHash = " + field + "Ordinals.clone();");
            out.line("private static final long[] " + field
                    + "Hashes = new long[" + field + "Ordinals.length];");
            out.beginBlock("static");
            out.beginBlock("for (int i = 0; i < " + field + "Ordinals.length; i++)");
            out.line(type + " constant = " + field + "Ordinals[i];");
            out.line("long hash = com.alibaba.fastjson2.util.Fnv.hashCode64(constant.name());");
            out.line("int j = i;");
            out.beginBlock("while (j > 0 && " + field + "Hashes[j - 1] > hash)");
            out.line(field + "Hashes[j] = " + field + "Hashes[j - 1];");
            out.line(field + "ByHash[j] = " + field + "ByHash[j - 1];");
            out.line("j--;");
            out.endBlock();
            out.line(field + "Hashes[j] = hash;");
            out.line(field + "ByHash[j] = constant;");
            out.endBlock();
            out.endBlock();
        }
    }

    void emitHelpers(JavaWriter out) {
        for (Map.Entry<String, String> entry : enums.entrySet()) {
            String type = entry.getKey();
            String field = entry.getValue();
            out.beginBlock("private static " + type + " " + field
                    + "(org.sjf4j.backend.fastjson2.binding.Fastjson2Parser parser)"
                    + " throws java.io.IOException");
            out.line("com.alibaba.fastjson2.JSONReader raw = parser.rawReader();");
            out.beginBlock("if (parser.nextIfNull())");
            out.line("return null;");
            out.endBlock();
            out.beginBlock("if (raw.isString())");
            out.line("long hash = raw.readValueHashCode();");
            out.line("int index = java.util.Arrays.binarySearch(" + field + "Hashes, hash);");
            out.beginBlock("if (index >= 0)");
            out.line("return " + field + "ByHash[index];");
            out.endBlock();
            out.line("throw new IllegalArgumentException("
                    + "\"No enum constant \" + " + type + ".class.getName()"
                    + " + \".\" + raw.getString());");
            out.endBlock();
            out.beginBlock("if (raw.isNumber())");
            out.line("int ordinal = parser.readIntValue();");
            out.beginBlock("if (ordinal >= 0 && ordinal < " + field + "Ordinals.length)");
            out.line("return " + field + "Ordinals[ordinal];");
            out.endBlock();
            out.line("throw new org.sjf4j.exception.BindingException("
                    + "\"enum ordinal '\" + ordinal + \"' out of range for type '\""
                    + " + " + type + ".class.getName() + \"'\");");
            out.endBlock();
            out.line("throw new org.sjf4j.exception.BindingException("
                    + "\"cannot read enum '\" + " + type + ".class.getName()"
                    + " + \"' from token '\" + parser.peekToken() + \"'\");");
            out.endBlock();
        }
    }
}
