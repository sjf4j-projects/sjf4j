package org.sjf4j.processor.binding;

import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.property.PropertyAccess;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import java.util.List;

/** Emits direct StreamingReader binding code. */
final class ReadEmitter {

    private static final String READER =
            "org.sjf4j.binding.StreamingReader";

    private static final String STREAMING_IO =
            "org.sjf4j.binding.StreamingIO";

    private static final String STREAMING_CONTEXT =
            "org.sjf4j.binding.StreamingContext";

    private final ProcessorContext context;

    ReadEmitter(ProcessorContext context) {
        this.context = context;
    }

    void emitMethod(
            JavaWriter out,
            BindingCompiler.CompiledMethod compiled) {

        BindingPlan plan = compiled.plan();
        BindingValue value = compiled.value();

        BindingSource.beginOverride(
                out,
                plan.method());

        String reader =
                BindingSource.parameterName(
                        plan,
                        plan.readerWriterParameter());

        out.line(reader + ".startDocument();");

        out.line(
                plan.valueType() +
                        " result = " +
                        readExpression(
                                value,
                                reader) +
                        ";");

        out.line(reader + ".endDocument();");
        out.line("return result;");

        BindingSource.endOverride(out);
    }

    void emitHelper(
            JavaWriter out,
            BindingValue value) {

        out.beginBlock(
                "private static " +
                        value.type() +
                        " " +
                        value.helperName() +
                        "(" + READER +
                        " reader) throws java.io.IOException");

        switch (value.kind()) {
            case POJO:
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
                throw new AssertionError(
                        "No read helper for " +
                                value.kind());
        }

        out.endBlock();
    }

    private void emitPojo(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startObject();");

        out.line(
                value.type() +
                        " value = new " +
                        value.type() +
                        "();");

        out.beginBlock(
                "while (!reader.nextIfObjectEnd())");

        out.line("String name = reader.nextName();");
        out.beginBlock("switch (name)");

        for (BindingProperty property :
                value.properties()) {

            out.line(
                    "case " +
                            JavaWriter.stringLiteral(
                                    property.name()) +
                            ":");

            out.indent();

            out.line(
                    assignment(
                            property.access(),
                            "value",
                            readExpression(
                                    property.value(),
                                    "reader")));

            out.line("break;");
            out.dedent();
        }

        out.line("default:");
        out.indent();
        out.line("reader.skipNext();");
        out.line("break;");
        out.dedent();

        out.endBlock();
        out.endBlock();

        out.line("return value;");
    }

    private void emitList(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startArray();");

        out.line(
                value.type() +
                        " value = new java.util.ArrayList<>();");

        out.beginBlock(
                "while (!reader.nextIfArrayEnd())");

        out.line(
                "value.add(" +
                        readExpression(
                                value.elementValue(),
                                "reader") +
                        ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitSet(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startArray();");

        out.line(
                value.type() +
                        " value = new java.util.LinkedHashSet<>();");

        out.beginBlock(
                "while (!reader.nextIfArrayEnd())");

        out.line(
                "value.add(" +
                        readExpression(
                                value.elementValue(),
                                "reader") +
                        ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitMap(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("reader.startObject();");

        out.line(
                value.type() +
                        " value = new java.util.LinkedHashMap<>();");

        out.beginBlock(
                "while (!reader.nextIfObjectEnd())");

        out.line("String name = reader.nextName();");

        out.line(
                "value.put(name, " +
                        readExpression(
                                value.mapValue(),
                                "reader") +
                        ");");

        out.endBlock();
        out.line("return value;");
    }

    private void emitNullReturn(JavaWriter out) {
        out.beginBlock("if (reader.nextIfNull())");
        out.line("return null;");
        out.endBlock();
    }

    private String readExpression(
            BindingValue value,
            String reader) {

        if (value.helperRequired()) {
            return value.helperName() +
                    "(" + reader + ")";
        }

        switch (value.kind()) {
            case STRING:
                return reader + ".nextStringOrNull()";

            case CHARACTER:
                if (value.primitive()) {
                    return reader + ".nextCharValue()";
                }
                return "(" + reader +
                        ".nextIfNull() ? null : java.lang.Character.valueOf(" +
                        reader + ".nextCharValue()))";

            case BOOLEAN:
                return value.primitive()
                        ? reader + ".nextBooleanValue()"
                        : reader + ".nextBoolean()";

            case BYTE:
                return value.primitive()
                        ? reader + ".nextByteValue()"
                        : reader + ".nextByte()";

            case SHORT:
                return value.primitive()
                        ? reader + ".nextShortValue()"
                        : reader + ".nextShort()";

            case INT:
                return value.primitive()
                        ? reader + ".nextIntValue()"
                        : reader + ".nextInt()";

            case LONG:
                return value.primitive()
                        ? reader + ".nextLongValue()"
                        : reader + ".nextLong()";

            case FLOAT:
                return value.primitive()
                        ? reader + ".nextFloatValue()"
                        : reader + ".nextFloat()";

            case DOUBLE:
                return value.primitive()
                        ? reader + ".nextDoubleValue()"
                        : reader + ".nextDouble()";

            case NUMBER:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        reader + ".nextNumber())";

            case BIG_INTEGER:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        reader + ".nextBigInteger())";

            case BIG_DECIMAL:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        reader + ".nextBigDecimal())";

            case ENUM:
                return "(" + reader +
                        ".nextIfNull() ? null : " +
                        value.type() +
                        ".valueOf(" +
                        reader +
                        ".nextString()))";

            case FALLBACK:
                return "(" + value.type() + ") " +
                        STREAMING_IO +
                        ".readNode(" +
                        reader + ", " +
                        typeExpression(
                                value.type()) +
                        ", " +
                        STREAMING_CONTEXT +
                        ".EMPTY)";

            default:
                throw new AssertionError(
                        "Unsupported read kind " +
                                value.kind());
        }
    }

    private String assignment(
            PropertyAccess access,
            String owner,
            String expression) {

        if (access.isMethod()) {
            return owner + '.' +
                    access.memberName() +
                    '(' + expression +
                    ");";
        }

        return owner + '.' +
                access.memberName() +
                " = " + expression +
                ";";
    }

    /**
     * Emits a java.lang.reflect.Type expression for generic fallback binding.
     */
    private String typeExpression(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY: {
                ArrayType array =
                        (ArrayType) type;

                TypeMirror component =
                        array.getComponentType();

                if (isReifiable(component)) {
                    return context.typeUtils
                            .erasure(type)
                            .toString() +
                            ".class";
                }

                return "new org.sjf4j.node.Types.GenericArrayTypeImpl(" +
                        typeExpression(component) +
                        ")";
            }

            case DECLARED: {
                DeclaredType declared =
                        (DeclaredType) type;

                List<? extends TypeMirror> arguments =
                        declared.getTypeArguments();

                if (arguments.isEmpty()) {
                    return context.typeUtils
                            .erasure(type)
                            .toString() +
                            ".class";
                }

                StringBuilder expression =
                        new StringBuilder();

                expression.append(
                        "new org.sjf4j.node.Types.ParameterizedTypeImpl(")
                        .append(context.typeUtils
                                .erasure(type))
                        .append(".class, new java.lang.reflect.Type[]{");

                for (int i = 0; i < arguments.size(); i++) {
                    if (i > 0) {
                        expression.append(", ");
                    }

                    expression.append(
                            typeExpression(
                                    arguments.get(i)));
                }

                expression.append("}, ");

                TypeMirror enclosing =
                        declared.getEnclosingType();

                if (enclosing == null ||
                        enclosing.getKind() ==
                                TypeKind.NONE) {
                    expression.append("null");
                } else {
                    expression.append(
                            typeExpression(enclosing));
                }

                expression.append(')');
                return expression.toString();
            }

            case WILDCARD: {
                WildcardType wildcard =
                        (WildcardType) type;

                TypeMirror upper =
                        wildcard.getExtendsBound();

                TypeMirror lower =
                        wildcard.getSuperBound();

                String upperExpression =
                        upper == null
                                ? "java.lang.Object.class"
                                : typeExpression(upper);

                String lowerExpression =
                        lower == null
                                ? ""
                                : typeExpression(lower);

                return "new org.sjf4j.node.Types.WildcardTypeImpl(" +
                        "new java.lang.reflect.Type[]{" +
                        upperExpression +
                        "}, new java.lang.reflect.Type[]{" +
                        lowerExpression +
                        "})";
            }

            default:
                return context.typeUtils
                        .erasure(type)
                        .toString() +
                        ".class";
        }
    }

    private boolean isReifiable(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return true;
        }

        if (type.getKind() ==
                TypeKind.ARRAY) {
            return isReifiable(
                    ((ArrayType) type)
                            .getComponentType());
        }

        if (type.getKind() !=
                TypeKind.DECLARED) {
            return false;
        }

        return ((DeclaredType) type)
                .getTypeArguments()
                .isEmpty();
    }
}
