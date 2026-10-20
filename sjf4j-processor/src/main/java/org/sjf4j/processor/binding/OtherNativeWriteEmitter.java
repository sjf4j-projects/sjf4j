package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

/** Native compiled writer emitter shared by Gson and JSON-P. */
final class OtherNativeWriteEmitter {

    private final BackendSpec backend;

    OtherNativeWriteEmitter(BackendSpec backend) {
        this.backend = backend;
    }

    void emitHelper(JavaWriter out, BindingValue value) {
        boolean gson = backend.backend() == org.sjf4j.annotation.binding.Backend.GSON;
        String generatorType = gson
                ? "com.google.gson.stream.JsonWriter"
                : "jakarta.json.stream.JsonGenerator";
        String beginObject = gson ? "beginObject" : "writeStartObject";
        String endObject = gson ? "endObject" : "writeEnd";
        String beginArray = gson ? "beginArray" : "writeStartArray";
        String endArray = gson ? "endArray" : "writeEnd";
        String nullMethod = gson ? "nullValue" : "writeNull";
        String nameMethod = gson ? "name" : "writeKey";
        out.beginBlock("private static void " + value.helperName() + "("
                + generatorType + " g, " + value.type() + " value) throws java.io.IOException");
        out.beginBlock("if (value == null)");
        out.line("g." + nullMethod + "();");
        out.line("return;");
        out.endBlock();
        switch (value.kind()) {
            case POJO:
                out.line("g." + beginObject + "();");
                for (BindingProperty property : value.properties()) {
                    String expr = WriteEmitter.propertyExpression(property, "value");
                    out.line("g." + nameMethod + "("
                            + JavaWriter.stringLiteral(property.name()) + ");");
                    emitValue(out, property.value(), expr);
                }
                out.line("g." + endObject + "();");
                break;
            case LIST:
            case SET:
                out.line("g." + beginArray + "();");
                out.beginBlock("for (" + value.elementValue().type() + " element : value)");
                emitValue(out, value.elementValue(), "element");
                out.endBlock();
                out.line("g." + endArray + "();");
                break;
            case MAP:
                out.line("g." + beginObject + "();");
                out.beginBlock("for (java.util.Map.Entry<?, ?> entry : value.entrySet())");
                out.line("g." + nameMethod + "((String) entry.getKey());");
                emitValue(out, value.mapValue(),
                        "(" + value.mapValue().type() + ") entry.getValue()");
                out.endBlock();
                out.line("g." + endObject + "();");
                break;
            default:
                throw new AssertionError(value.kind());
        }
        out.endBlock();
    }

    private void emitValue(JavaWriter out, BindingValue child, String expr) {
        boolean gson = backend.backend() == org.sjf4j.annotation.binding.Backend.GSON;
        String scalarMethod = gson ? "value" : "write";
        String nullMethod = gson ? "nullValue" : "writeNull";
        if (child.helperRequired()) {
            out.line(child.helperName() + "(g, " + expr + ");");
            return;
        }
        if (child.kind() == BindingValue.Kind.STRING) {
            out.line("_writeString(g, " + expr + ");");
            return;
        }
        if (child.kind() == BindingValue.Kind.ENUM || child.kind() == BindingValue.Kind.CHARACTER) {
            if (child.primitive()) {
                out.line("g." + scalarMethod + "(Character.toString(" + expr + "));");
            } else {
                out.beginBlock("");
                out.line(child.type() + " item = " + expr + ";");
                out.beginBlock("if (item == null)");
                out.line("g." + nullMethod + "();");
                out.endBlock(" else {");
                out.indent();
                out.line("g." + scalarMethod + "(item."
                        + (child.kind() == BindingValue.Kind.ENUM ? "name()" : "toString()")
                        + ");");
                out.endBlock();
                out.endBlock();
            }
            return;
        }
        String valueExpr = "item";
        if (child.primitive()) {
            String rawExpr = expr;
            if (!gson && (child.kind() == BindingValue.Kind.BYTE
                    || child.kind() == BindingValue.Kind.SHORT)) {
                rawExpr = "(int) (" + expr + ")";
            } else if (!gson && child.kind() == BindingValue.Kind.FLOAT) {
                rawExpr = "(double) (" + expr + ")";
            }
            out.line("g." + scalarMethod + "(" + rawExpr + ");");
        } else {
            out.beginBlock("");
            out.line(child.type() + " item = " + expr + ";");
            out.beginBlock("if (item == null)");
            out.line("g." + nullMethod + "();");
            out.endBlock(" else {");
            out.indent();
            if (!gson && (child.kind() == BindingValue.Kind.BYTE
                    || child.kind() == BindingValue.Kind.SHORT)) {
                valueExpr = "item.intValue()";
            } else if (child.kind() == BindingValue.Kind.FLOAT) {
                // Both adapters widen boxed float to double before serialization.
                valueExpr = "item.doubleValue()";
            }
            out.line("g." + scalarMethod + "(" + valueExpr + ");");
            out.endBlock();
            out.endBlock();
        }
    }


}
