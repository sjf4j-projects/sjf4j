package org.sjf4j.processor.binding;

import org.sjf4j.processor.code.JavaWriter;

import java.util.HashSet;
import java.util.Set;

/**
 * Compile-time code generation for the streaming-only JSON backend.
 *
 * The native JSON backends use StructuralWriteEmitter instead. This emitter
 * keeps the streaming generator, separator and whole-root fallback rules intact.
 * No generated runtime abstraction is introduced.
 */
final class StreamingWriteEmitter {

    private static final String STREAMING_IO = "org.sjf4j.binding.StreamingIO";

    private final BackendSpec backend;

    StreamingWriteEmitter(BackendSpec backend) {
        this.backend = backend;
    }

    boolean shouldFallbackRoot(BindingValue value) {
        return !fullyConcreteGraph(value, new HashSet<BindingValue>())
                && !containsAdaptedValue(value, new HashSet<BindingValue>());
    }

    void emitHelper(JavaWriter out, BindingValue value) {
        out.beginBlock("private void " + value.helperName()
                + "(" + backend.generatorType() + " generator, "
                + value.type() + " value) throws java.io.IOException");
        switch (value.kind()) {
            case ONE_OF:
                emitOneOf(out, value);
                break;
            case POJO:
            case JOJO:
                emitPojo(out, value);
                break;
            case JSON_OBJECT:
            case JSON_ARRAY:
                out.line(STREAMING_IO + ".writeNode(generator, value, "
                        + "this.context);");
                break;
            case LIST:
            case SET:
                emitCollection(out, value);
                break;
            case MAP:
                emitMap(out, value);
                break;
            default:
                throw new AssertionError("No write helper for " + value.kind());
        }
        out.endBlock();
    }

    private void emitOneOf(JavaWriter out, BindingValue value) {
        emitNullReturn(out);
        boolean hasMappedBranch = false;
        for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
            if (subtype.compiled != null || subtype.cachedPojo) {
                hasMappedBranch = true;
                break;
            }
        }
        if (hasMappedBranch) {
            out.line("Class<?> actualClass = value.getClass();");
        }
        int index = 0;
        for (BindingValue.OneOfSubtype subtype : value.oneOfSubtypes()) {
            String metadata = value.helperName() + "_subtype" + index++ + "_pojoInfo";
            if (subtype.compiled == null && !subtype.cachedPojo) continue;
            String type = subtype.type.toString();
            out.beginBlock("if (actualClass == " + type + ".class)");
            String expression = "(" + type + ") value";
            if (subtype.cachedPojo) {
                out.line(STREAMING_IO + ".writePojo(generator, value, " + metadata
                        + ", this.context);");
            } else {
                writeValue(out, subtype.compiled, "generator", expression);
            }
            out.line("return;");
            out.endBlock();
        }
        // Unknown classes and subtypes with inaccessible fields retain Runtime semantics.
        out.line(STREAMING_IO + ".writeNode(generator, value, this.context);");
    }


    private void emitPojo(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("generator.writeObjectStart();");

        int index = 0;

        for (BindingProperty property :
                value.properties()) {

            if (index > 0) {
                out.line("generator.writeObjectSeparator();");
            }

            out.line("generator.writeName(" +
                    (backend.usesCompiledNames()
                            ? WriteEmitter.compiledNameField(value, index)
                            : JavaWriter.stringLiteral(property.name())) +
                    ");");
            index++;

            writeValue(
                    out,
                    property.value(),
                    "generator",
                    WriteEmitter.propertyExpression(property, "value"));
        }

        if (value.kind() == BindingValue.Kind.JOJO && value.writeDynamic()) {
            out.line("int count = " + index + ";");
            out.beginBlock("for (java.util.Map.Entry<String, Object> entry : "
                    + "value.dynamicMembers().entrySet())");
            out.line("generator.writeName(entry.getKey(), count++ > 0);");
            out.line(STREAMING_IO + ".writeNode(generator, entry.getValue(), "
                    + "this.context);");
            out.endBlock();
        }

        out.line("generator.writeObjectEnd();");
    }

    private void emitCollection(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("generator.writeArrayStart();");
        out.line("int count = 0;");

        out.beginBlock(
                "for (" +
                        value.elementValue().type() +
                        " element : value)");

        out.beginBlock("if (count++ > 0)");
        out.line("generator.writeArraySeparator();");
        out.endBlock();

        writeValue(
                out,
                value.elementValue(),
                "generator",
                "element");

        out.endBlock();
        out.line("generator.writeArrayEnd();");
    }

    private void emitMap(
            JavaWriter out,
            BindingValue value) {

        emitNullReturn(out);
        out.line("generator.writeObjectStart();");
        out.line("int count = 0;");

        out.beginBlock(
                "for (java.util.Map.Entry<?, ?> entry : value.entrySet())");

        out.beginBlock("if (count++ > 0)");
        out.line("generator.writeObjectSeparator();");
        out.endBlock();

        out.line(
                "generator.writeName((String) entry.getKey());");

        writeValue(
                out,
                value.mapValue(),
                "generator",
                "(" +
                        value.mapValue().type() +
                        ") entry.getValue()");

        out.endBlock();
        out.line("generator.writeObjectEnd();");
    }

    void writeValue(
            JavaWriter out,
            BindingValue value,
            String generator,
            String expression) {

        if (value.helperRequired()) {
            // Dynamic containers without adapted descendants have no generated
            // helper. A mixed compiled parent can still reach them.
            if (!requiresWriteHelper(value)) {
                out.line(STREAMING_IO + ".writeNode(" + generator + ", "
                        + expression + ", this.context);");
                return;
            }
            // All remaining helpers use the same (generator, value) convention.
            out.line(value.helperName() + "(" + generator + ", " + expression + ");");
            return;
        }

        switch (value.kind()) {
            case VALUE_CODEC:
                CodecWriteEmitter.emit(out, value, expression, generator, null, backend.backend());
                return;
            case STRING:
                out.line(
                        generator +
                                ".writeString(" +
                                expression +
                                ");");
                return;

            case CHARACTER:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Char");
                return;

            case BOOLEAN:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Boolean");
                return;

            case BYTE:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Byte");
                return;

            case SHORT:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Short");
                return;

            case INT:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Int");
                return;

            case LONG:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Long");
                return;

            case FLOAT:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Float");
                return;

            case DOUBLE:
                emitPrimitiveOrBoxed(
                        out,
                        value,
                        generator,
                        expression,
                        "Double");
                return;

            case NUMBER:
                out.line(
                        generator +
                                ".writeNumber(" +
                                expression +
                                ");");
                return;

            case BIG_INTEGER:
                out.line(
                        generator +
                                ".writeBigInteger(" +
                                expression +
                                ");");
                return;

            case BIG_DECIMAL:
                out.line(
                        generator +
                                ".writeBigDecimal(" +
                                expression +
                                ");");
                return;

            case ENUM:
                out.beginBlock("");
                out.line(value.type() + " item = " + expression + ";");
                out.beginBlock("if (item == null)");
                out.line(generator + ".writeNull();");
                out.endBlock(" else {");
                out.indent();
                out.line(generator + ".writeStringValue(item.name());");
                out.endBlock();
                out.endBlock();
                return;

            case RUNTIME:
                out.line(
                        STREAMING_IO +
                                ".writeNode(" +
                                generator + ", " +
                                expression + ", " +
                                "this.context);");
                return;

            default:
                throw new AssertionError(
                        "Unsupported write kind " +
                                value.kind());
        }
    }

    private void emitPrimitiveOrBoxed(
            JavaWriter out,
            BindingValue value,
            String generator,
            String expression,
            String suffix) {

        out.line(
                generator +
                        (value.primitive()
                                ? ".write" + suffix + "Value("
                                : ".write" + suffix + "(") +
                        expression +
                        ");");
    }

    private void emitNullReturn(JavaWriter out) {
        out.beginBlock("if (value == null)");
        out.line("generator.writeNull();");
        out.line("return;");
        out.endBlock();
    }


    /** JSON backends always compile structural helpers; streaming-only paths may fall back. */
    boolean requiresWriteHelper(BindingValue value) {
        return fullyConcreteGraph(value, new HashSet<BindingValue>())
                || containsAdaptedValue(value, new HashSet<BindingValue>());
    }

    /** Streaming-only backends may need helpers for adapted descendants. */
    private boolean containsAdaptedValue(BindingValue value,
            Set<BindingValue> visited) {
        if (!visited.add(value)) return false;
        switch (value.kind()) {
            case VALUE_CODEC:
            case ONE_OF:
            case JOJO:
            case JSON_OBJECT:
            case JSON_ARRAY:
                return true;
            case POJO:
                for (BindingProperty property : value.properties()) {
                    if (containsAdaptedValue(property.value(), visited)) return true;
                }
                return false;
            case LIST:
            case SET:
                return containsAdaptedValue(value.elementValue(), visited);
            case MAP:
                return containsAdaptedValue(value.mapValue(), visited);
            default:
                return false;
        }
    }

    private boolean fullyConcreteGraph(BindingValue value,
            Set<BindingValue> visited) {
        if (!visited.add(value)) return true;
        switch (value.kind()) {
            case RUNTIME:
            case ONE_OF:
            case NUMBER:
            case VALUE_CODEC:
            case JOJO:
            case JSON_OBJECT:
            case JSON_ARRAY:
                return false;
            case POJO:
                for (BindingProperty p : value.properties()) {
                    if (!fullyConcreteGraph(p.value(), visited)) return false;
                }
                return true;
            case LIST:
            case SET:
                return fullyConcreteGraph(value.elementValue(), visited);
            case MAP:
                return fullyConcreteGraph(value.mapValue(), visited);
            default:
                return true;
        }
    }


}
