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
    private final BindingNames names;
    private final WriteEmitter writes;
    private final StructuralWriteEmitter structures;

    OtherNativeWriteEmitter(BackendSpec backend, BindingNames names, WriteEmitter writes) {
        this.backend = backend;
        this.names = names;
        this.writes = writes;
        this.structures = new StructuralWriteEmitter(this, names);
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
                  String generator, NameAllocator names) {
        if (value.helperRequired()) {
            out.beginBlock("if (" + expression + " == null)");
            out.line(generator + ".writeNull();");
            out.endBlock(" else {");
            out.indent();
            out.line(this.names.primaryHelper(value) + "(" + generator + ", " + expression + ");");
            out.endBlock();
            return;
        }

        String nativeGenerator = names.newName("nativeGenerator");
        if (StructuralWriteEmitter.needsNativeValue(value, backend.backend(), false)) {
            out.line(generatorType() + " " + nativeGenerator + " = " + generator + ".nativeGenerator();");
        }
        emitValue(out, value, expression, generator, nativeGenerator, names);
    }

    /** All structural helpers share the (generator, value) signature. */
    void emitHelper(JavaWriter out, BindingValue value) {
        out.beginBlock("private void " + this.names.primaryHelper(value) + "("
                + backend.generatorType() + " generator, " + value.type()
                + " value) throws java.io.IOException");
        if (StructuralWriteEmitter.needsNativeGenerator(value, backend.backend())) {
            out.line(generatorType() + " nativeGenerator = generator.nativeGenerator();");
        }
        structures.emitHelperBody(out, value);
        out.endBlock();
    }

    @Override
    public boolean includeNulls() { return writes.includeNulls(); }

    @Override
    public String runtimeConfig() { return writes.runtimeConfig(); }

    @Override
    public void startObject(JavaWriter out) {
        out.line("nativeGenerator." + (gson() ? "beginObject" : "writeStartObject") + "();");
    }

    @Override
    public void endObject(JavaWriter out) {
        out.line("nativeGenerator." + (gson() ? "endObject" : "writeEnd") + "();");
    }

    @Override
    public void startArray(JavaWriter out) {
        out.line("nativeGenerator." + (gson() ? "beginArray" : "writeStartArray") + "();");
    }

    @Override
    public void endArray(JavaWriter out) {
        out.line("nativeGenerator." + (gson() ? "endArray" : "writeEnd") + "();");
    }

    @Override
    public void writeNull(JavaWriter out) {
        out.line("nativeGenerator." + (gson() ? "nullValue" : "writeNull") + "();");
    }

    @Override
    public void writePropertyName(JavaWriter out, BindingValue owner, int index) {
        writeDynamicName(out, JavaWriter.stringLiteral(owner.properties().get(index).name()));
    }

    @Override
    public void writeDynamicName(JavaWriter out, String expression) {
        out.line("nativeGenerator." + (gson() ? "name" : "writeKey") + "(" + expression + ");");
    }

    @Override
    public void writeMapName(JavaWriter out, String expression) {
        writeDynamicName(out, expression);
    }

    @Override
    public void writeRuntimeValue(JavaWriter out, String expression) {
        out.line(STREAMING_IO + ".writeNode(generator, " + expression
                + ", " + writes.runtimeConfig() + ");");
    }

    @Override
    public void writeValue(JavaWriter out, BindingValue child, String expression) {
        emitValue(out, child, expression, "generator", "nativeGenerator", null);
    }

    @Override
    public void writeNonNullValue(JavaWriter out, BindingValue value, String expression) {
        if (value.helperRequired()) {
            out.line(this.names.primaryHelper(value) + "(generator, " + expression + ");");
            return;
        }
        String method = gson() ? "value" : "write";
        switch (value.kind()) {
            case VALUE_CODEC:
                CodecWriteEmitter.emit(out, value, expression, "generator", null,
                        backend.backend(), names, writes.runtimeConfig(), true);
                return;
            case RUNTIME:
                writeRuntimeValue(out, expression);
                return;
            case NUMBER:
                out.line("generator.writeNumber(" + expression + ");");
                return;
            case ENUM:
                out.line("nativeGenerator." + method + "(" + expression + ".name());");
                return;
            case STRING:
                out.line("nativeGenerator." + method + "(" + expression + ");");
                return;
            case CHARACTER:
                out.line("nativeGenerator." + method + "(" + expression + ".toString());");
                return;
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_INTEGER:
            case BIG_DECIMAL:
                String arg = expression;
                if (!gson() && (value.kind() == BindingValue.Kind.BYTE
                        || value.kind() == BindingValue.Kind.SHORT)) {
                    arg += ".intValue()";
                } else if (value.kind() == BindingValue.Kind.FLOAT) {
                    arg += ".doubleValue()";
                }
                out.line("nativeGenerator." + method + "(" + arg + ");");
                return;
            default:
                throw new AssertionError(value.kind());
        }
    }

    private void emitValue(JavaWriter out, BindingValue child, String expression,
                           String generator, String nativeGenerator, NameAllocator names) {
        // Method parameters are reserved for root values; helpers use their local scope.
        String item = names == null ? "item" : names.newName("item");
        if (child.helperRequired()) {
            out.beginBlock("if (" + expression + " == null)");
            out.line(generator + ".writeNull();");
            out.endBlock(" else {");
            out.indent();
            out.line(this.names.primaryHelper(child) + "(" + generator + ", " + expression + ");");
            out.endBlock();
            return;
        }

        String method = gson() ? "value" : "write";
        if (child.kind() == BindingValue.Kind.STRING) {
            out.line(this.names.sharedHelper("writeString") + "(" + nativeGenerator + ", " + expression + ");");
            return;
        }
        switch (child.kind()) {
            case VALUE_CODEC:
                CodecWriteEmitter.emit(out, child, expression, generator, null, backend.backend(), this.names, writes.runtimeConfig());
                return;
            case RUNTIME:
                out.line(STREAMING_IO + ".writeNode(" + generator + ", " + expression
                        + ", " + writes.runtimeConfig() + ");");
                return;
            case NUMBER:
                out.line(generator + ".writeNumber(" + expression + ");");
                return;
            case ENUM:
            case CHARACTER:
                if (child.primitive()) {
                    out.line(nativeGenerator + "." + method + "(Character.toString(" + expression + "));");
                } else {
                    out.beginBlock("");
                    out.line(child.type() + " " + item + " = " + expression + ";");
                    out.beginBlock("if (" + item + " == null)");
                    out.line(nativeGenerator + "." + (gson() ? "nullValue" : "writeNull") + "();");
                    out.endBlock(" else {");
                    out.indent();
                    out.line(nativeGenerator + "." + method + "(" + item + "."
                            + (child.kind() == BindingValue.Kind.ENUM ? "name()" : "toString()") + ");");
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
            out.line(nativeGenerator + "." + method + "(" + arg + ");");
        } else {
            out.beginBlock("");
            out.line(child.type() + " " + item + " = " + expression + ";");
            out.beginBlock("if (" + item + " == null)");
            out.line(nativeGenerator + "." + (gson() ? "nullValue" : "writeNull") + "();");
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
            out.line(nativeGenerator + "." + method + "(" + arg + ");");
            out.endBlock();
            out.endBlock();
        }
    }
}
