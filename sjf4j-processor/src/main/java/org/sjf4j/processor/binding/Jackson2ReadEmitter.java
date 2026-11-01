package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

/**
 * Jackson 2 native traversal used by compiled readers.
 *
 * <p>POJO construction, creator arguments, property assignment and value decoding
 * remain in {@link ReadEmitter}. This helper owns only Jackson-specific cursor
 * movement and the native array-element handoff.</p>
 */
final class Jackson2ReadEmitter {

    void emitObjectStart(JavaWriter out, boolean insideObject) {
        if (insideObject) {
            out.line("com.fasterxml.jackson.core.JsonParser nativeParser = parser.nativeParser();");
        } else {
            emitNativeStart(out, "START_OBJECT");
        }
    }

    void emitArrayStart(JavaWriter out) {
        emitNativeStart(out, "START_ARRAY");
    }

    String nameMatchExpression(String matcherField) {
        return matcherField + ".matchNext(nativeParser, expectedNameIndex)";
    }

    /** Enter a native container from FIELD_NAME, an array element, or document root. */
    private void emitNativeStart(JavaWriter out, String kind) {
        out.line("com.fasterxml.jackson.core.JsonParser nativeParser = parser.nativeParser();");
        out.beginBlock("if (nativeParser.currentToken() != com.fasterxml.jackson.core.JsonToken." + kind + ")");
        out.beginBlock("if (nativeParser.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_NULL)");
        out.line("return null;");
        out.endBlock();
        out.beginBlock("if (nativeParser.nextToken() != com.fasterxml.jackson.core.JsonToken." + kind + ")");
        out.beginBlock("if (nativeParser.currentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_NULL)");
        out.line("return null;");
        out.endBlock();
        out.line("throw new java.io.IOException(\"expected " + kind
                + " but was \" + nativeParser.currentToken());");
        out.endBlock();
        out.endBlock();
    }

    /** Native array loops advance once. Wrapped scalar/runtime reads adopt that token. */
    void emitArrayLoopStart(JavaWriter out, BindingValue element) {
        out.beginBlock("while (nativeParser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY)");
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

    /** String elements can be read from the native cursor without prefetched handoff. */
    String arrayElementReadExpression(BindingValue element, String normalRead) {
        return element.kind() == BindingValue.Kind.STRING
                ? "parser.readCurrentString()" : normalRead;
    }
}
