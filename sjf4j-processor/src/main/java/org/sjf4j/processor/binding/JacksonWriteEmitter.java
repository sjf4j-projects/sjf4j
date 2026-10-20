package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

/** Native compiled writer emitter shared by Jackson 2 and Jackson 3. */
final class JacksonWriteEmitter {

    private final BackendSpec backend;

    JacksonWriteEmitter(BackendSpec backend) {
        this.backend = backend;
    }

    private String jacksonGeneratorType() {
        return backend.backend() == org.sjf4j.annotation.binding.Backend.JACKSON2
                ? "com.fasterxml.jackson.core.JsonGenerator"
                : "tools.jackson.core.JsonGenerator";
    }


    void emitHelper(JavaWriter out, BindingValue value) {
        boolean jackson2 = backend.backend() == org.sjf4j.annotation.binding.Backend.JACKSON2;
        out.beginBlock("private static void " + value.helperName() + "("
                + jacksonGeneratorType() + " g, " + value.type()
                + " value) throws java.io.IOException");
        out.beginBlock("if (value == null)");
        out.line("g.writeNull();");
        out.line("return;");
        out.endBlock();
        switch (value.kind()) {
            case POJO:
                out.line("g.writeStartObject();");
                int index = 0;
                for (BindingProperty property : value.properties()) {
                    String field = WriteEmitter.compiledNameField(value, index++) + ".serializedName";
                    BindingValue child = property.value();
                    String expr = WriteEmitter.propertyExpression(property, "value");
                    out.line("g." + (jackson2 ? "writeFieldName" : "writeName")
                            + "(" + field + ");");
                    emitValue(out, child, expr);
                }
                out.line("g.writeEndObject();");
                break;
            case LIST:
                out.line("g.writeStartArray();");
                out.beginBlock("for (int i = 0, n = value.size(); i < n; i++)");
                emitValue(out, value.elementValue(), "value.get(i)");
                out.endBlock();
                out.line("g.writeEndArray();");
                break;
            case SET:
                out.line("g.writeStartArray();");
                out.beginBlock("for (" + value.elementValue().type() + " element : value)");
                emitValue(out, value.elementValue(), "element");
                out.endBlock();
                out.line("g.writeEndArray();");
                break;
            case MAP:
                out.line("g.writeStartObject();");
                out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
                out.line("g." + (jackson2 ? "writeFieldName" : "writeName")
                        + "((String) entry.getKey());");
                emitValue(out, value.mapValue(),
                        "(" + value.mapValue().type() + ") entry.getValue()");
                out.endBlock();
                out.line("g.writeEndObject();");
                break;
            default:
                throw new AssertionError(value.kind());
        }
        out.endBlock();
    }


    private void emitValue(JavaWriter out, BindingValue child, String expr) {
        if (child.helperRequired()) {
            out.line(child.helperName() + "(g, " + expr + ");");
            return;
        }
        if (child.kind() == BindingValue.Kind.STRING) {
            out.line("_writeString(g, " + expr + ");");
            return;
        }
        String method;
        switch (child.kind()) {
            case BOOLEAN: method = "writeBoolean"; break;
            case BYTE: case SHORT: case INT: case LONG:
            case FLOAT: case DOUBLE: case BIG_INTEGER: case BIG_DECIMAL:
                method = "writeNumber"; break;
            case ENUM:
                out.beginBlock("");
                out.line(child.type() + " item = " + expr + ";");
                out.line("g.writeString(item == null ? null : item.name());");
                out.endBlock();
                return;
            case CHARACTER:
                if (child.primitive()) {
                    out.line("g.writeString(Character.toString(" + expr + "));");
                } else {
                    out.beginBlock("");
                    out.line(child.type() + " item = " + expr + ";");
                    out.line("g.writeString(item == null ? null : item.toString());");
                    out.endBlock();
                }
                return;
            default:
                throw new AssertionError("Unsupported Jackson native value " + child.kind());
        }
        if (child.primitive()) {
            out.line("g." + method + "(" + expr + ");");
        } else {
            out.beginBlock("");
            out.line(child.type() + " item = " + expr + ";");
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
