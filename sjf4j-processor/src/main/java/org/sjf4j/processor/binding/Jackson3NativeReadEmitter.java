package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.code.JavaWriter;

import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Native Jackson 3 cursor reads for fully static POJO trees.
 *
 * <p>Unlike the general compiled reader, the generated helper accepts Jackson's
 * JsonParser directly. Like the Fastjson2 compiled reader, supported scalars
 * and enums are read from the native cursor, with dedicated helpers for values
 * requiring special semantics. A POJO is selected only when every descendant
 * can use this single-cursor protocol; no wrapper/native token handoff occurs.
 * Creators, dynamic nodes, codecs and OneOf retain the original compiled path.</p>
 */
final class Jackson3NativeReadEmitter {

    private static final String TOKEN = "tools.jackson.core.JsonToken";
    private static final String MATCHER = "tools.jackson.core.sym.PropertyNameMatcher";

    private final BindingNames names;
    private final Map<String, TypeMirror> enums = new LinkedHashMap<>();

    Jackson3NativeReadEmitter(BindingNames names) {
        this.names = names;
    }

    void register(BindingValue root) {
        if (!supports(root)) return;
        Set<BindingValue> visited =
                Collections.newSetFromMap(new IdentityHashMap<BindingValue, Boolean>());
        register(root, visited);
    }

    private void register(BindingValue value, Set<BindingValue> visited) {
        if (value == null || !visited.add(value)
                || value.direction() != BindingPlan.Direction.READ_FROM) return;
        if (value.kind() == BindingValue.Kind.ENUM) {
            enums.putIfAbsent(value.type().toString(), value.type());
        }
        for (BindingProperty property : value.properties()) {
            register(property.value(), visited);
        }
        register(value.elementValue(), visited);
    }

    void emitEnumFields(GeneratedClass generated) {
        if (enums.isEmpty()) return;
        generated.addField(out -> {
            for (TypeMirror type : enums.values()) {
                out.line("private static final " + type + "[] " + enumValuesField(type)
                        + " = " + type + ".values();");
            }
        });
        generated.addHelper(this::emitEnumHelpers);
    }

    private String enumValuesField(TypeMirror type) {
        return names.enumField(type, "jackson3NativeOrdinals",
                BindingNames.fieldType(type) + "_NATIVE_ENUM_ORDINALS");
    }

    private String enumReadHelper(TypeMirror type) {
        return names.enumHelper(type, "jackson3NativeRead",
                "read" + BindingNames.simpleType(type) + "NativeEnum");
    }

    private void emitEnumHelpers(JavaWriter out) {
        for (TypeMirror type : enums.values()) {
            String ordinals = enumValuesField(type);
            out.blank();
            out.beginBlock("private static " + type + " " + enumReadHelper(type)
                    + "(tools.jackson.core.JsonParser parser) throws java.io.IOException");
            out.line(TOKEN + " token = parser.currentToken();");
            out.beginBlock("if (token == " + TOKEN + ".VALUE_NULL)");
            out.line("return null;");
            out.endBlock();
            out.beginBlock("if (token == " + TOKEN + ".VALUE_STRING)");
            out.line("return java.lang.Enum.valueOf(" + type + ".class, parser.getString());");
            out.endBlock();
            out.beginBlock("if (token == " + TOKEN + ".VALUE_NUMBER_INT"
                    + " || token == " + TOKEN + ".VALUE_NUMBER_FLOAT)");
            out.line("int ordinal = parser.getIntValue();");
            out.beginBlock("if (ordinal >= 0 && ordinal < " + ordinals + ".length)");
            out.line("return " + ordinals + "[ordinal];");
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

    boolean supports(BindingValue value) {
        return supports(value, true);
    }

    /** Baseline shapes with independently measured 1/10/100KB gains. */
    boolean supportsBaseline(BindingValue value) {
        return supports(value, false);
    }

    private boolean supports(BindingValue value, boolean expanded) {
        Set<BindingValue> visiting =
                Collections.newSetFromMap(new IdentityHashMap<BindingValue, Boolean>());
        return supports(value, visiting, expanded);
    }

    private boolean supports(BindingValue value, Set<BindingValue> visiting,
                             boolean expanded) {
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
                case CHARACTER:
                case BYTE:
                case SHORT:
                case NUMBER:
                case BIG_INTEGER:
                case BIG_DECIMAL:
                case ENUM:
                    return expanded;
                case LIST:
                    return supports(value.elementValue(), visiting, expanded);
                case POJO:
                    if (value.creator() != null || value.properties().isEmpty()) return false;
                    for (BindingProperty property : value.properties()) {
                        if (!supports(property.value(), visiting, expanded)) return false;
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
        String numbers = "org.sjf4j.util.Numbers.";
        String nativeSupport = "org.sjf4j.backend.jackson3.binding.Jackson3NativeReadSupport.";
        switch (value.kind()) {
            case STRING:
                return "org.sjf4j.backend.jackson3.binding.Jackson3NativeReadSupport.readString(parser)";
            case CHARACTER:
                return nativeSupport + (value.primitive()
                        ? "readCharValue(parser)" : "readChar(parser)");
            case BYTE:
                return value.primitive()
                        ? numbers + "toByte(parser.getNumberValue())"
                        : "(" + nullCheck + " ? null : "
                        + numbers + "toByte(parser.getNumberValue()))";
            case SHORT:
                return value.primitive()
                        ? numbers + "toShort(parser.getNumberValue())"
                        : "(" + nullCheck + " ? null : "
                        + numbers + "toShort(parser.getNumberValue()))";
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
            case NUMBER:
                return "(" + nullCheck + " ? null : parser.getNumberValue())";
            case BIG_INTEGER:
                return "(" + nullCheck + " ? null : parser.getBigIntegerValue())";
            case BIG_DECIMAL:
                return "(" + nullCheck + " ? null : parser.getDecimalValue())";
            case ENUM:
                return enumReadHelper(value.type()) + "(parser)";
            default:
                throw new AssertionError("Unsupported native Jackson3 scalar: " + value.kind());
        }
    }
}
