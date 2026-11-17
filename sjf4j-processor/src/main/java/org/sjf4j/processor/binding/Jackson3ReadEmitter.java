package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

/**
 * Jackson 3 native container traversal for compiled readers.
 *
 * <p>Field matching and scalar decoding still use Jackson3Parser so Jackson 3's
 * reader/UTF-8-specific name matcher behavior remains unchanged. This emitter
 * only bypasses wrapper probes for container boundaries and array traversal.</p>
 */
final class Jackson3ReadEmitter implements NativeReadEmitter {

    @Override
    public void emitObjectStart(JavaWriter out, boolean insideObject) {
        if (insideObject) {
            out.line("tools.jackson.core.JsonParser nativeParser = parser.nativeParser();");
        } else {
            emitNativeStart(out, "START_OBJECT");
        }
    }

    @Override
    public void emitArrayStart(JavaWriter out) {
        emitNativeStart(out, "START_ARRAY");
    }

    private void emitNativeStart(JavaWriter out, String kind) {
        out.line("tools.jackson.core.JsonParser nativeParser = parser.nativeParser();");
        out.beginBlock("if (nativeParser.currentToken() != tools.jackson.core.JsonToken." + kind + ")");
        out.beginBlock("if (nativeParser.currentToken() == tools.jackson.core.JsonToken.VALUE_NULL)");
        out.line("return null;");
        out.endBlock();
        out.beginBlock("if (nativeParser.nextToken() != tools.jackson.core.JsonToken." + kind + ")");
        out.beginBlock("if (nativeParser.currentToken() == tools.jackson.core.JsonToken.VALUE_NULL)");
        out.line("return null;");
        out.endBlock();
        out.line("throw new java.io.IOException(\"expected " + kind
                + " but was \" + nativeParser.currentToken());");
        out.endBlock();
        out.endBlock();
    }

    @Override
    public void emitArrayLoopStart(JavaWriter out, BindingValue element) {
        out.beginBlock("while (nativeParser.nextToken() != tools.jackson.core.JsonToken.END_ARRAY)");
        boolean nativeElement = element.helperRequired()
                && (((element.kind() == BindingValue.Kind.POJO
                        || element.kind() == BindingValue.Kind.JOJO)
                        && !element.properties().isEmpty())
                    || element.kind() == BindingValue.Kind.ARRAY
                    || element.kind() == BindingValue.Kind.LIST
                    || element.kind() == BindingValue.Kind.SET);
        if (!nativeElement && element.kind() != BindingValue.Kind.STRING) {
            out.line("parser.reuseCurrentToken();");
        }
    }

    @Override
    public String arrayElementReadExpression(BindingValue element, String normalRead) {
        return element.kind() == BindingValue.Kind.STRING
                ? "parser.readCurrentString()" : normalRead;
    }
}
