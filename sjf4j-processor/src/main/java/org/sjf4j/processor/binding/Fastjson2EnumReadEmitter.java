package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fastjson2-only compiled Enum value recognition.
 *
 * <p>Hash and ordinal tables are initialized once per Enum type. Generated
 * reads consume Fastjson2's native value hash without building a String
 * on successful matches. The generic ReadEmitter remains backend-neutral.</p>
 */
final class Fastjson2EnumReadEmitter {

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
            out.line("org.sjf4j.binding.StreamingParser.Token token = parser.peekToken();");
            out.beginBlock("if (token == org.sjf4j.binding.StreamingParser.Token.NULL)");
            out.line("parser.nextIfNull();");
            out.line("return null;");
            out.endBlock();
            out.beginBlock("if (token == org.sjf4j.binding.StreamingParser.Token.STRING)");
            out.line("long hash = parser.readValueHashCode();");
            out.line("int index = java.util.Arrays.binarySearch(" + field + "Hashes, hash);");
            out.beginBlock("if (index >= 0)");
            out.line("return " + field + "ByHash[index];");
            out.endBlock();
            out.line("throw new IllegalArgumentException("
                    + "\"No enum constant \" + " + type + ".class.getName()"
                    + " + \".\" + parser.getHashedString());");
            out.endBlock();
            out.beginBlock("if (token == org.sjf4j.binding.StreamingParser.Token.NUMBER)");
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
                    + " + \"' from token '\" + token + \"'\");");
            out.endBlock();
        }
    }
}
