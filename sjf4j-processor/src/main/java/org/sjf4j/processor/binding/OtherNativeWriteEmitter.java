package org.sjf4j.processor.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.processor.code.NameAllocator;

/**
 * Gson and JSON-P write adapters for the shared compile-time structural
 * traversal. Only native token operations differ; generated code does not
 * depend on StructuralWriteEmitter at runtime.
 */
final class OtherNativeWriteEmitter implements StructuralWriteEmitter.Operations {

    private static final String STREAMING_IO = "org.sjf4j.binding.StreamingIO";

    private final BackendSpec backend;
    private final StructuralWriteEmitter structures = new StructuralWriteEmitter(this);

    OtherNativeWriteEmitter(BackendSpec backend) {
        this.backend = backend;
    }

    private boolean gson() {
        return backend.backend() == Backend.GSON;
    }

    private String generatorType() {
        return gson() ? "com.google.gson.stream.JsonWriter"
                : "jakarta.json.stream.JsonGenerator";
    }

    /** Handles Gson/JSON-P root scalars without the streaming-only emitter. */
    void emitRoot(JavaWriter out, BindingValue value, String expression,
                  String writer, NameAllocator names) {
        if (value.helperRequired()) {
            out.line(value.helperName() + "(" + writer + ", " + expression + ");");
            return;
        }

        String generator = names.newName("g");
        switch (value.kind()) {
            case VALUE_CODEC:
            case NUMBER:
            case RUNTIME:
                // These paths do not need a native generator local.
                break;
            default:
                out.line(generatorType() + " " + generator + " = " + writer + ".nativeWriter();");
        }
        emitValue(out, value, expression, generator, names);
    }

    /** All structural helpers share the (writer, value) signature. */
    void emitHelper(JavaWriter out, BindingValue value) {
        out.beginBlock("private void " + value.helperName() + "("
                + backend.generatorType() + " writer, " + value.type()
                + " value) throws java.io.IOException");
        out.line(generatorType() + " g = writer.nativeWriter();");

        switch (value.kind()) {
            case JSON_OBJECT:
            case JSON_ARRAY:
                writeRuntimeValue(out, "value");
                break;
            case ONE_OF:
                structures.emitOneOf(out, value);
                break;
            case POJO:
            case JOJO:
            case LIST:
            case SET:
            case MAP:
                out.beginBlock("if (value == null)");
                writeNull(out);
                out.line("return;");
                out.endBlock();
                switch (value.kind()) {
                    case POJO:
                    case JOJO:
                        structures.emitPojo(out, value);
                        break;
                    case LIST:
                        structures.emitList(out, value);
                        break;
                    case SET:
                        structures.emitSet(out, value);
                        break;
                    case MAP:
                        structures.emitMap(out, value);
                        break;
                    default:
                        throw new AssertionError(value.kind());
                }
                break;
            default:
                throw new AssertionError(value.kind());
        }
        out.endBlock();
    }

    @Override
    public void startObject(JavaWriter out) {
        out.line("g." + (gson() ? "beginObject" : "writeStartObject") + "();");
    }

    @Override
    public void endObject(JavaWriter out) {
        out.line("g." + (gson() ? "endObject" : "writeEnd") + "();");
    }

    @Override
    public void startArray(JavaWriter out) {
        out.line("g." + (gson() ? "beginArray" : "writeStartArray") + "();");
    }

    @Override
    public void endArray(JavaWriter out) {
        out.line("g." + (gson() ? "endArray" : "writeEnd") + "();");
    }

    @Override
    public void writeNull(JavaWriter out) {
        out.line("g." + (gson() ? "nullValue" : "writeNull") + "();");
    }

    @Override
    public void writePropertyName(JavaWriter out, BindingValue owner, int index) {
        writeDynamicName(out, JavaWriter.stringLiteral(owner.properties().get(index).name()));
    }

    @Override
    public void writeDynamicName(JavaWriter out, String expression) {
        out.line("g." + (gson() ? "name" : "writeKey") + "(" + expression + ");");
    }

    @Override
    public void writeMapName(JavaWriter out, String expression) {
        writeDynamicName(out, expression);
    }

    @Override
    public void writeRuntimeValue(JavaWriter out, String expression) {
        out.line(STREAMING_IO + ".writeNode(writer, " + expression + ", this.context);");
    }

    @Override
    public void writeValue(JavaWriter out, BindingValue child, String expression) {
        emitValue(out, child, expression, "g", null);
    }

    private void emitValue(JavaWriter out, BindingValue child, String expression,
                           String generator, NameAllocator names) {
        // Method parameters are reserved for root values; helpers use their local scope.
        String item = names == null ? "item" : names.newName("item");
        if (child.helperRequired()) {
            out.line(child.helperName() + "(writer, " + expression + ");");
            return;
        }

        String method = gson() ? "value" : "write";
        if (child.kind() == BindingValue.Kind.STRING) {
            out.line("_writeString(" + generator + ", " + expression + ");");
            return;
        }
        switch (child.kind()) {
            case VALUE_CODEC:
                CodecWriteEmitter.emit(out, child, expression, "writer", null, backend.backend());
                return;
            case RUNTIME:
                writeRuntimeValue(out, expression);
                return;
            case NUMBER:
                out.line("writer.writeNumber(" + expression + ");");
                return;
            case ENUM:
            case CHARACTER:
                if (child.primitive()) {
                    out.line(generator + "." + method + "(Character.toString(" + expression + "));");
                } else {
                    out.beginBlock("");
                    out.line(child.type() + " " + item + " = " + expression + ";");
                    out.beginBlock("if (" + item + " == null)");
                    out.line(generator + "." + (gson() ? "nullValue" : "writeNull") + "();");
                    out.endBlock(" else {");
                    out.indent();
                    out.line(generator + "." + method + "(" + item + "."
                            + (child.kind() == BindingValue.Kind.ENUM ? "name()" : "toString()")
                            + ");");
                    out.endBlock();
                    out.endBlock();
                }
                return;
            default:
                break;
        }

        if (child.primitive()) {
            String arg = expression;
            if (!gson() && (child.kind() == BindingValue.Kind.BYTE
                    || child.kind() == BindingValue.Kind.SHORT)) {
                arg = "(int) (" + expression + ")";
            } else if (!gson() && child.kind() == BindingValue.Kind.FLOAT) {
                arg = "(double) (" + expression + ")";
            }
            out.line(generator + "." + method + "(" + arg + ");");
        } else {
            out.beginBlock("");
            out.line(child.type() + " " + item + " = " + expression + ";");
            out.beginBlock("if (" + item + " == null)");
            out.line(generator + "." + (gson() ? "nullValue" : "writeNull") + "();");
            out.endBlock(" else {");
            out.indent();
            String arg = item;
            if (!gson() && (child.kind() == BindingValue.Kind.BYTE
                    || child.kind() == BindingValue.Kind.SHORT)) {
                arg = item + ".intValue()";
            } else if (child.kind() == BindingValue.Kind.FLOAT) {
                // Preserve the previous widening semantics for boxed floats.
                arg = item + ".doubleValue()";
            }
            out.line(generator + "." + method + "(" + arg + ");");
            out.endBlock();
            out.endBlock();
        }
    }
}
