package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;

/**
 * Jackson 2/3 compiled write generation.
 *
 * Every structural helper accepts the Jackson StreamingWriter and its value.
 * Each helper obtains the native generator locally. A codec, OneOf or dynamic
 * child changes only that child's emission; it never changes the parent's
 * POJO/List/Set/Map implementation or the helper signature.
 */
final class JacksonWriteEmitter {

    private static final String STREAMING_IO = "org.sjf4j.binding.StreamingIO";

    private final BackendSpec backend;

    JacksonWriteEmitter(BackendSpec backend) {
        this.backend = backend;
    }

    private boolean jackson2() {
        return backend.backend() == Backend.JACKSON2;
    }

    private String generatorType() {
        return jackson2()
                ? "com.fasterxml.jackson.core.JsonGenerator"
                : "tools.jackson.core.JsonGenerator";
    }

    /** Writes a root scalar or delegates a root structural value. */
    void emitRoot(JavaWriter out, BindingValue value, String expression, String writer) {
        out.line(generatorType() + " g = " + writer + ".nativeWriter();");
        emitValue(out, value, expression, writer);
    }

    /**
     * Generates all structural nodes using the same writer/value convention.
     * Instance helpers are needed by codec metadata and local runtime fallbacks.
     */
    void emitHelper(JavaWriter out, BindingValue value) {
        out.beginBlock("private void " + value.helperName() + "("
                + backend.writerType() + " writer, " + value.type()
                + " value) throws java.io.IOException");
        out.line(generatorType() + " g = writer.nativeWriter();");

        if (value.kind() == BindingValue.Kind.ONE_OF) {
            emitOneOf(out, value);
        } else if (value.kind() == BindingValue.Kind.JSON_OBJECT
                || value.kind() == BindingValue.Kind.JSON_ARRAY) {
            out.line(STREAMING_IO + ".writeNode(writer, value, this.context);");
        } else {
            out.beginBlock("if (value == null)");
            out.line("g.writeNull();");
            out.line("return;");
            out.endBlock();

            switch (value.kind()) {
                case POJO:
                case JOJO:
                    emitPojo(out, value);
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
                    throw new AssertionError(value.kind());
            }
        }
        out.endBlock();
    }

    private void emitPojo(JavaWriter out, BindingValue value) {
        out.line("g.writeStartObject();");
        int index = 0;
        for (BindingProperty property : value.properties()) {
            BindingValue child = property.value();
            String expr = WriteEmitter.propertyExpression(property, "value");
            if (!child.primitive()) {
                // A getter must be evaluated once on either null-policy path.
                String propertyVar = "property" + index;
                out.line(child.type() + " " + propertyVar + " = " + expr + ";");
                out.beginBlock("if (this.context.includeNulls || " + propertyVar + " != null)");
                expr = propertyVar;
            }

            out.line("g." + (jackson2() ? "writeFieldName" : "writeName") + "("
                    + WriteEmitter.compiledNameField(value, index) + ".serializedName);");
            emitValue(out, child, expr, "writer");
            if (!child.primitive()) out.endBlock();
            index++;
        }

        if (value.kind() == BindingValue.Kind.JOJO && value.writeDynamic()) {
            out.beginBlock("for (java.util.Map.Entry<String, Object> entry : "
                    + "value.dynamicProperties().entrySet())");
            out.line("Object dynamicValue = entry.getValue();");
            out.beginBlock("if (this.context.includeNulls || dynamicValue != null)");
            out.line("g." + (jackson2() ? "writeFieldName" : "writeName")
                    + "(entry.getKey());");
            out.line(STREAMING_IO + ".writeNode(writer, dynamicValue, this.context);");
            out.endBlock();
            out.endBlock();
        }
        out.line("g.writeEndObject();");
    }

    /** Same List traversal for all element kinds, including codec and OneOf. */
    private void emitList(JavaWriter out, BindingValue value) {
        out.line("g.writeStartArray();");
        out.beginBlock("if (value instanceof java.util.RandomAccess)");
        out.beginBlock("for (int i = 0, n = value.size(); i < n; i++)");
        emitValue(out, value.elementValue(), "value.get(i)", "writer");
        out.endBlock();
        out.endBlock(" else {");
        out.indent();
        out.beginBlock("for (" + value.elementValue().type() + " element : value)");
        emitValue(out, value.elementValue(), "element", "writer");
        out.endBlock();
        out.endBlock();
        out.line("g.writeEndArray();");
    }

    private void emitSet(JavaWriter out, BindingValue value) {
        out.line("g.writeStartArray();");
        out.beginBlock("for (" + value.elementValue().type() + " element : value)");
        emitValue(out, value.elementValue(), "element", "writer");
        out.endBlock();
        out.line("g.writeEndArray();");
    }

    private void emitMap(JavaWriter out, BindingValue value) {
        out.line("g.writeStartObject();");
        out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
        out.line("Object mapValue = entry.getValue();");
        out.beginBlock("if (this.context.includeNulls || mapValue != null)");
        out.line("g." + (jackson2() ? "writeFieldName" : "writeName")
                + "((String) entry.getKey());");
        emitValue(out, value.mapValue(),
                "(" + value.mapValue().type() + ") mapValue", "writer");
        out.endBlock();
        out.endBlock();
        out.line("g.writeEndObject();");
    }

    /** Compiled subtype dispatch uses the same two-argument helpers as POJO/List. */
    private void emitOneOf(JavaWriter out, BindingValue value) {
        out.beginBlock("if (value == null)");
        out.line("g.writeNull();");
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
                out.line(STREAMING_IO + ".writePojo(writer, value, " + metadata
                        + ", this.context);");
            } else {
                emitValue(out, subtype.compiled, "(" + type + ") value", "writer");
            }
            out.line("return;");
            out.endBlock();
        }

        // Unknown and inaccessible subtypes preserve runtime OneOf semantics.
        out.line(STREAMING_IO + ".writeNode(writer, value, this.context);");
    }

    /**
     * Emits a value into the current generator, delegating only the leaf that
     * needs Codec/Runtime handling. Structural helpers always accept
     * (writer, value), never a separately passed native generator.
     */
    private void emitValue(JavaWriter out, BindingValue child,
                           String expression, String writer) {
        if (child.helperRequired()) {
            out.line(child.helperName() + "(" + writer + ", " + expression + ");");
            return;
        }
        switch (child.kind()) {
            case VALUE_CODEC:
                CodecWriteEmitter.emit(out, child, expression, writer, "g", backend.backend());
                return;
            case RUNTIME:
                out.line(STREAMING_IO + ".writeNode(" + writer + ", " + expression
                        + ", this.context);");
                return;
            case STRING:
                out.line("_writeString(g, " + expression + ");");
                return;
            case NUMBER:
                out.line(writer + ".writeNumber(" + expression + ");");
                return;
            case ENUM:
                out.beginBlock("");
                out.line(child.type() + " item = " + expression + ";");
                out.line("g.writeString(item == null ? null : item.name());");
                out.endBlock();
                return;
            case CHARACTER:
                if (child.primitive()) {
                    out.line("g.writeString(Character.toString(" + expression + "));");
                } else {
                    out.beginBlock("");
                    out.line(child.type() + " item = " + expression + ";");
                    out.line("g.writeString(item == null ? null : item.toString());");
                    out.endBlock();
                }
                return;
            default:
                break;
        }

        String method;
        switch (child.kind()) {
            case BOOLEAN:
                method = "writeBoolean";
                break;
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_INTEGER:
            case BIG_DECIMAL:
                method = "writeNumber";
                break;
            default:
                throw new AssertionError("Unsupported Jackson value " + child.kind());
        }
        if (child.primitive()) {
            out.line("g." + method + "(" + expression + ");");
        } else {
            out.beginBlock("");
            out.line(child.type() + " item = " + expression + ";");
            out.beginBlock("if (item == null)");
            out.line("g.writeNull();");
            out.endBlock(" else {");
            out.indent();
            out.line("g." + method + "(item);");
            out.endBlock();
            out.endBlock();
        }
    }
}
