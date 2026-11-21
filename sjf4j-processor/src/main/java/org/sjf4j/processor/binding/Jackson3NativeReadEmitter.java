package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Native Jackson 3 cursor reads for fully static POJO trees.
 *
 * <p>Unlike the general compiled reader, the generated helper accepts Jackson's
 * JsonParser directly. Every descendant must be supported before this path is
 * selected, so nested values never switch cursor ownership halfway through an
 * object. The existing wrapper helpers remain responsible for creators,
 * dynamic nodes, codecs, OneOf, and other advanced binding semantics.</p>
 */
final class Jackson3NativeReadEmitter {

    private static final String TOKEN = "tools.jackson.core.JsonToken";
    private static final String MATCHER = "tools.jackson.core.sym.PropertyNameMatcher";

    private final BindingNames names;

    Jackson3NativeReadEmitter(BindingNames names) {
        this.names = names;
    }

    boolean supports(BindingValue value) {
        Set<BindingValue> visiting =
                Collections.newSetFromMap(new IdentityHashMap<BindingValue, Boolean>());
        return supports(value, visiting);
    }

    private boolean supports(BindingValue value, Set<BindingValue> visiting) {
        if (value == null || !visiting.add(value)) return false;
        try {
            switch (value.kind()) {
                case STRING:
                case BOOLEAN:
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                    return true;
                case LIST:
                    return supports(value.elementValue(), visiting);
                case POJO:
                    if (value.creator() != null || value.properties().isEmpty()) return false;
                    for (BindingProperty property : value.properties()) {
                        if (!supports(property.value(), visiting)) return false;
                    }
                    return true;
                default:
                    return false;
            }
        } finally {
            visiting.remove(value);
        }
    }

    String helperName(BindingValue value) {
        return names.helper(value, "jackson3NativeRead", value.helperName() + "Native");
    }

    String matcherFieldName(BindingValue value) {
        return names.field(value, "jackson3NativeMatcher",
                BindingNames.fieldType(value.type()) + "_NATIVE_NAME_MATCHER");
    }

    void emitHelper(JavaWriter out, BindingValue value, ReadEmitter reads) {
        out.blank();
        out.beginBlock("private " + value.type() + " " + helperName(value)
                + "(tools.jackson.core.JsonParser parser) throws java.io.IOException");
        switch (value.kind()) {
            case POJO:
                emitPojo(out, value, reads);
                break;
            case LIST:
                emitList(out, value);
                break;
            default:
                throw new AssertionError("Unsupported native Jackson3 helper: " + value.kind());
        }
        out.endBlock();
    }

    private void emitPojo(JavaWriter out, BindingValue value, ReadEmitter reads) {
        emitContainerStart(out, "START_OBJECT");
        out.line(value.type() + " value = new " + value.type() + "();");
        out.line("int nameIndex;");
        out.beginBlock("while ((nameIndex = parser.nextNameMatchAndToken("
                + matcherFieldName(value) + ")) != " + MATCHER + ".MATCH_END_OBJECT)");

        // Only primary names are part of the native quad matcher; aliases must
        // still resolve through SJF4J's declared name metadata.
        out.beginBlock("if (nameIndex == " + MATCHER + ".MATCH_UNKNOWN_NAME)");
        out.line("int aliasIndex = " + reads.matcherFieldName(value)
                + ".fallback(parser.currentName());");
        out.beginBlock("if (aliasIndex < 0)");
        out.line("parser.nextToken();");
        out.line("parser.skipChildren();");
        out.line("continue;");
        out.endBlock();
        out.line("nameIndex = aliasIndex;");
        out.line("parser.nextToken();");
        out.endBlock();

        // The switch default handles all unexpected matcher results, including
        // negative sentinel values other than MATCH_UNKNOWN_NAME.
        out.beginBlock("switch (nameIndex)");
        int index = 0;
        for (BindingProperty property : value.properties()) {
            out.line("case " + index++ + ":");
            out.indent();
            reads.emitPropertyReadExpression(out, property, "value",
                    readExpression(property.value()));
            out.line("break;");
            out.dedent();
        }
        out.line("default:");
        out.indent();
        out.line("throw new java.io.IOException(\"Unexpected property matcher result \" + nameIndex);");
        out.dedent();
        out.endBlock();
        out.endBlock();
        out.line("return value;");
    }

    private void emitList(JavaWriter out, BindingValue value) {
        emitContainerStart(out, "START_ARRAY");
        out.line(value.type() + " value = new java.util.ArrayList<>();");
        out.line(TOKEN + " elementToken;");
        out.beginBlock("while ((elementToken = parser.nextToken()) != " + TOKEN + ".END_ARRAY)");
        out.beginBlock("if (elementToken == null)");
        out.line("throw new java.io.IOException(\"Unexpected EOF in array\");");
        out.endBlock();
        out.line("value.add(" + readExpression(value.elementValue()) + ");");
        out.endBlock();
        out.line("return value;");
    }

    private static void emitContainerStart(JavaWriter out, String kind) {
        out.line(TOKEN + " token = parser.currentToken();");
        out.beginBlock("if (token == null || token == " + TOKEN + ".PROPERTY_NAME)");
        out.line("token = parser.nextToken();");
        out.endBlock();
        out.beginBlock("if (token == " + TOKEN + ".VALUE_NULL)");
        out.line("return null;");
        out.endBlock();
        out.beginBlock("if (token != " + TOKEN + "." + kind + ")");
        out.line("throw new java.io.IOException(\"Expected " + kind + " but was \" + token);");
        out.endBlock();
    }

    private String readExpression(BindingValue value) {
        if (value.helperRequired()) {
            return helperName(value) + "(parser)";
        }
        String nullCheck = "parser.currentToken() == " + TOKEN + ".VALUE_NULL";
        switch (value.kind()) {
            case STRING:
                return "org.sjf4j.backend.jackson3.binding.Jackson3NativeReadSupport.readString(parser)";
            case BOOLEAN:
                return value.primitive() ? "parser.getBooleanValue()"
                        : "(" + nullCheck + " ? null : parser.getBooleanValue())";
            case INT:
                return value.primitive() ? "parser.getIntValue()"
                        : "(" + nullCheck + " ? null : parser.getIntValue())";
            case LONG:
                return value.primitive() ? "parser.getLongValue()"
                        : "(" + nullCheck + " ? null : parser.getLongValue())";
            case FLOAT:
                return value.primitive() ? "parser.getFloatValue()"
                        : "(" + nullCheck + " ? null : parser.getFloatValue())";
            case DOUBLE:
                return value.primitive() ? "parser.getDoubleValue()"
                        : "(" + nullCheck + " ? null : parser.getDoubleValue())";
            default:
                throw new AssertionError("Unsupported native Jackson3 scalar: " + value.kind());
        }
    }
}
