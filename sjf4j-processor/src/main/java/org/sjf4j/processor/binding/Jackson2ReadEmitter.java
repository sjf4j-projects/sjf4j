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

    /** Native array loops advance once. String elements use the token directly. */
    void emitArrayLoopStart(JavaWriter out, BindingValue element) {
        if (element.kind() == BindingValue.Kind.STRING) {
            out.line("com.fasterxml.jackson.core.JsonToken elementToken;");
            out.beginBlock("while ((elementToken = nativeParser.nextToken()) != com.fasterxml.jackson.core.JsonToken.END_ARRAY)");
            out.beginBlock("if (elementToken != com.fasterxml.jackson.core.JsonToken.VALUE_STRING"
                    + " && elementToken != com.fasterxml.jackson.core.JsonToken.VALUE_NULL)");
            out.line("throw new java.io.IOException(\"expected string or null, but was \" + elementToken);");
            out.endBlock();
            return;
        }
        out.beginBlock("while (nativeParser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY)");
        boolean nativeElement = element.helperRequired()
                && (((element.kind() == BindingValue.Kind.POJO
                        || element.kind() == BindingValue.Kind.JOJO)
                        && !element.properties().isEmpty())
                    || element.kind() == BindingValue.Kind.ARRAY
                    || element.kind() == BindingValue.Kind.LIST
                    || element.kind() == BindingValue.Kind.SET);
        if (!nativeElement) {
            out.line("parser.reuseCurrentToken();");
        }
    }

    /** The string-element loop already checked VALUE_STRING or VALUE_NULL. */
    String arrayElementReadExpression(BindingValue element, String normalRead) {
        return element.kind() == BindingValue.Kind.STRING
                ? "(elementToken == com.fasterxml.jackson.core.JsonToken.VALUE_NULL ? null : nativeParser.getText())"
                : normalRead;
    }
}
