package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

/**
 * Shared compile-time traversal templates for structural JSON nodes.
 *
 * These callbacks run only during annotation processing. Generated helpers
 * still call the selected backend's native API directly; there is no runtime
 * operation interface or extra virtual dispatch.
 *
 * The enclosing helper declares its writer and native generator. Backend
 * implementations own token operations, field names and separators.
 */
final class StructuralWriteEmitter {

    interface Operations {
        void startObject(JavaWriter out);
        void endObject(JavaWriter out);
        void startArray(JavaWriter out);
        void endArray(JavaWriter out);
        void writeNull(JavaWriter out);
        void writePropertyName(JavaWriter out, BindingValue owner, int index);
        default boolean supportsStringMember() { return false; }
        default void writeStringMember(JavaWriter out, BindingValue owner, int index,
                                       String expression) { throw new UnsupportedOperationException(); }
        void writeDynamicName(JavaWriter out, String expression);
        void writeMapName(JavaWriter out, String expression);
        void writeValue(JavaWriter out, BindingValue value, String expression);
        void writeRuntimeValue(JavaWriter out, String expression);

        // Jackson writes delimiters automatically. Backends with explicit
        // separators may override these compile-time hooks; counters are then
        // generated only for those backends.
        default boolean explicitObjectSeparators() { return false; }
        default boolean explicitArraySeparators() { return false; }
        default void beforeObjectMember(JavaWriter out, String ordinal) {}
        default void beforeArrayElement(JavaWriter out, String ordinal) {}
    }

    private final Operations ops;

    StructuralWriteEmitter(Operations ops) {
        this.ops = ops;
    }

    void emitPojo(JavaWriter out, BindingValue value) {
        ops.startObject(out);
        if (ops.explicitObjectSeparators()) out.line("int memberCount = 0;");
        int index = 0;
        for (BindingProperty property : value.properties()) {
            BindingValue child = property.value();
            String expr = WriteEmitter.propertyExpression(property, "value");
            if (!child.primitive()) {
                // Evaluate a getter once, including when null properties are omitted.
                String propertyVar = "property" + index;
                out.line(child.type() + " " + propertyVar + " = " + expr + ";");
                out.beginBlock("if (this.context.includeNulls || " + propertyVar + " != null)");
                expr = propertyVar;
            }

            if (ops.explicitObjectSeparators()) {
                ops.beforeObjectMember(out, "memberCount++");
            }
            if (child.kind() == BindingValue.Kind.STRING && ops.supportsStringMember()) {
                ops.writeStringMember(out, value, index, expr);
            } else {
                ops.writePropertyName(out, value, index);
                ops.writeValue(out, child, expr);
            }
            if (!child.primitive()) out.endBlock();
            index++;
        }

        if (value.kind() == BindingValue.Kind.JOJO && value.writeDynamic()) {
            out.beginBlock("for (java.util.Map.Entry<String, Object> entry : "
                    + "value.dynamicProperties().entrySet())");
            out.line("Object dynamicValue = entry.getValue();");
            out.beginBlock("if (this.context.includeNulls || dynamicValue != null)");
            if (ops.explicitObjectSeparators()) {
                ops.beforeObjectMember(out, "memberCount++");
            }
            ops.writeDynamicName(out, "entry.getKey()");
            ops.writeRuntimeValue(out, "dynamicValue");
            out.endBlock();
            out.endBlock();
        }
        ops.endObject(out);
    }

    /** Random-access and sequential lists use the same element emission. */
    void emitList(JavaWriter out, BindingValue value) {
        ops.startArray(out);
        out.beginBlock("if (value instanceof java.util.RandomAccess)");
        out.beginBlock("for (int i = 0, n = value.size(); i < n; i++)");
        if (ops.explicitArraySeparators()) ops.beforeArrayElement(out, "i");
        ops.writeValue(out, value.elementValue(), "value.get(i)");
        out.endBlock();
        out.endBlock(" else {");
        out.indent();
        if (ops.explicitArraySeparators()) out.line("int elementCount = 0;");
        out.beginBlock("for (" + value.elementValue().type() + " element : value)");
        if (ops.explicitArraySeparators()) {
            ops.beforeArrayElement(out, "elementCount++");
        }
        ops.writeValue(out, value.elementValue(), "element");
        out.endBlock();
        out.endBlock();
        ops.endArray(out);
    }

    void emitSet(JavaWriter out, BindingValue value) {
        ops.startArray(out);
        if (ops.explicitArraySeparators()) out.line("int elementCount = 0;");
        out.beginBlock("for (" + value.elementValue().type() + " element : value)");
        if (ops.explicitArraySeparators()) {
            ops.beforeArrayElement(out, "elementCount++");
        }
        ops.writeValue(out, value.elementValue(), "element");
        out.endBlock();
        ops.endArray(out);
    }

    void emitMap(JavaWriter out, BindingValue value) {
        ops.startObject(out);
        if (ops.explicitObjectSeparators()) out.line("int memberCount = 0;");
        out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
        out.line("Object mapValue = entry.getValue();");
        out.beginBlock("if (this.context.includeNulls || mapValue != null)");
        if (ops.explicitObjectSeparators()) {
            ops.beforeObjectMember(out, "memberCount++");
        }
        ops.writeMapName(out, "(String) entry.getKey()");
        ops.writeValue(out, value.mapValue(),
                "(" + value.mapValue().type() + ") mapValue");
        out.endBlock();
        out.endBlock();
        ops.endObject(out);
    }

    void emitOneOf(JavaWriter out, BindingValue value) {
        out.beginBlock("if (value == null)");
        ops.writeNull(out);
        out.line("return;");
        out.endBlock();

        boolean knownSubtype = false;
        for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
            if (subtype.compiled != null || subtype.cachedPojo) {
                knownSubtype = true;
                break;
            }
        }
        if (knownSubtype) out.line("Class<?> actualClass = value.getClass();");
        int index = 0;
        for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
            String metadata = value.helperName() + "_subtype" + index++ + "_pojoInfo";
            if (subtype.compiled == null && !subtype.cachedPojo) continue;
            String type = subtype.type.toString();
            out.beginBlock("if (actualClass == " + type + ".class)");
            if (subtype.cachedPojo) {
                out.line("org.sjf4j.binding.StreamingIO.writePojo(writer, value, "
                        + metadata + ", this.context);");
            } else {
                ops.writeValue(out, subtype.compiled, "(" + type + ") value");
            }
            out.line("return;");
            out.endBlock();
        }

        // Preserve runtime handling for unknown or inaccessible subtypes.
        ops.writeRuntimeValue(out, "value");
    }
}
